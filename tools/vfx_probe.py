#!/usr/bin/env python3
"""Isolated real-GPU shader probe; NOT a Minecraft FPS benchmark.

Requires moderngl, numpy, Pillow (install in a disposable venv).
Example: python tools/vfx_probe.py --baseline /tmp/opencode/flake-shockwave-before.fsh
Renders the actual fragment shader against a synthetic reverse-Z scene, including side
views, Low/High detail and occlusion. Uses GL_TIME_ELAPSED; excludes CPU upload and blit.
"""
import argparse
import itertools
import json
import math
from pathlib import Path

import moderngl
import numpy as np
from PIL import Image

VERTEX = """#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location=0) out vec2 texCoord;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    texCoord = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
"""
SCENE = """#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 color;
layout(location=1) out float depth;
uniform mat4 vp; uniform mat4 inv; uniform vec3 eye; uniform int wall;
void main() {
    vec4 q = inv * vec4(texCoord * 2.0 - 1.0, 0.0, 1.0);
    vec3 ray = normalize(q.xyz / q.w);
    float hit = ray.y < -0.00001 ? -eye.y / ray.y : 1e6;
    vec3 p = eye + ray * hit;
    float grid = mod(floor(p.x) + floor(p.z), 2.0);
    color = hit < 1e5 ? vec4(mix(vec3(0.12,0.15,0.17),vec3(0.21,0.24,0.26),grid),1)
                     : vec4(0.08,0.12,0.18,1);
    if (wall > 0) {
        float wallZ = wall == 1 ? -2.0 : (wall == 2 ? 2.0 : 4.0);
        vec3 a = (vec3(-8.0,0.0,wallZ) - eye) / ray;
        vec3 b = (vec3(8.0,5.0,wallZ+0.6) - eye) / ray;
        vec3 lo = min(a,b), hi = max(a,b);
        float entry = max(max(lo.x,lo.y),lo.z), exit = min(min(hi.x,hi.y),hi.z);
        if (entry > 0.0 && entry < exit && entry < hit) { hit = entry; color = vec4(0.24,0.21,0.19,1); }
    }
    vec4 clip = vp * vec4(ray * hit,1);
    depth = hit < 1e5 ? clip.z / clip.w * 0.5 + 0.5 : 0.0;
}
"""


def matrices(eye, target, aspect):
    forward = np.array(target, dtype=float) - eye
    forward /= np.linalg.norm(forward)
    right = np.cross(forward, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, forward)
    view = np.eye(4); view[:3, :3] = [right, up, -forward]
    f = 1 / math.tan(math.radians(65) / 2); near, far = .1, 200
    proj = np.array([[f/aspect, 0, 0, 0], [0, f, 0, 0],
                     [0, 0, (far+near)/(far-near), 2*far*near/(far-near)], [0, 0, -1, 0]])
    vp = proj @ view
    return vp, np.linalg.inv(vp)


def front(age):
    t = min(1, max(0, age / .5))
    return .35 + 8.65 * t*t*(3-2*t)


def ground_channels(u, eye, age, quality, wave):
    """Bounded flat-ground fixture for the current cached-channel UBO, not the terrain sampler."""
    def arrival(z):
        low, high = 0., 1.
        for _ in range(16):
            t = (low + high) / 2
            if .35 + 8.65*t*t*(3-2*t) < z: low = t
            else: high = t
        return (low + high) * .25
    shift = np.array([wave*.25, 0, wave*.1]) - eye
    def put(offset, xyz, w): u[offset:offset+4] = [*(np.array(xyz) + shift), w]
    def segment(group, slot, a, b, delay=0):
        birth, end = arrival(a[2])+delay, max(arrival(a[2])+delay+.008, arrival(b[2])+delay)
        if age < birth or age > end+.16: return
        power = max(0, (b[2]-.35)/8.65)**1.5
        alpha = max(0, 1-max(0, age-end)/.16)**2 * (.55+power)
        reveal = min(1, (age-birth)/(end-birth))
        entry = wave*64+group*8+slot
        put(1608+entry*4, a, (.022+power*.045)*(1 if delay == 0 else .72))
        put(2632+entry*4, np.array(a)+(np.array(b)-a)*reveal, alpha)
    for g in range(8):
        z = .12+g*1.11
        a, b, c = [.12*math.sin(g*5), .065, z], [-.2*math.sin(g*4+1), .065, z+.555], [.15*math.sin(g*5+2), .065, z+1.11]
        segment(g, 0, a, b); segment(g, 1, b, c)
        reach = .3+g*.17; side = 1 if g%2 else -1
        tip = [side*reach, .065, min(9, z+.65)]
        bend = [side*reach*.45, .065, z+.4]
        segment(g, 2, a, bend, .02); segment(g, 3, bend, tip, .03)
        if quality >= 1 and g >= 3:
            bend = [-side*reach*.55, .065, min(9,z+.85)]
            segment(g, 4, b, bend, .025); segment(g, 5, bend, [-side*reach,.065,min(9,z+1)], .04)
        if quality >= 2 and g >= 4:
            peak = [c[0]+.12,.3+g*.08,c[2]-.08]
            segment(g, 6, c, peak, .018); segment(g, 7, peak, c, .03)
        put(3656+(wave*8+g)*4, [0,.75,z+.55], 2.25)


def uniforms(size, vp, inv, eye, kind, age, quality=2, count=1, fixtures=None):
    u = np.zeros(size // 4, dtype='f4')
    u[:16] = vp.T.flatten(); u[16:32] = inv.T.flatten()
    u[32:36] = [2, -1, quality, 4 + quality*4]
    if kind == 'impact':
        u[68:72] = [.65, 1, .5, .5]
        return u.tobytes()
    for i in range(count):
        origin = np.array([i*.25, 0 if kind == 'quake' else 1, i*.1]) - eye
        radius = front(age) if kind == 'quake' else (1.5 if kind == 'charge' else 5)
        u[36+i*4:40+i*4] = [*origin, radius]
        style = 52+i*4
        u[style:style+4] = [1, age, 2 if kind == 'quake' else 1 if kind == 'charge' else 0,
                            math.pi/2 if kind == 'quake' else age/1.1]
        if kind.startswith('ghoul-'):
            # Replay the actual client rig, not a second implementation of its procedural animation.
            data, saved_eye = fixtures[kind]
            material, bounds = 1096+i*128, 3656+i*32
            body_world = data[1096:1099]+saved_eye
            shift = saved_eye-body_world-eye+np.array([i*.25, 0, i*.1])
            u[style:style+4] = data[52:56]
            u[style+1] += age-.3
            u[material:material+128] = data[1096:1224]
            for slot in (0, 4, 5, 6, 7, 8):
                u[material+slot*4:material+slot*4+3] += shift
            for j in range(64):
                start, end = 1608+(i*64+j)*4, 2632+(i*64+j)*4
                u[start:start+4] = data[1608+j*4:1612+j*4]
                u[end:end+4] = data[2632+j*4:2636+j*4]
                u[start:start+3] += shift; u[end:end+3] += shift
                if quality == 0 and (32 <= j < 40 or 40 <= j < 56 and j % 8 >= 4):
                    u[start+3] = u[end+3] = 0
            for j in range(8):
                u[bounds+j*4:bounds+j*4+4] = data[3656+j*4:3660+j*4]
                u[bounds+j*4:bounds+j*4+3] += shift
            continue
        if kind in ('ram-hit', 'ram-miss', 'bulwark', 'bulwark-inside', 'bulwark-release'):
            # Bastion uses spare entries in the existing UBO; keep these fixtures in sync with writeRam/writeBuff.
            bounds, material = 3656+i*32, 1096+i*128
            shift = np.array([i*.25, 0, i*.1])
            def point(offset, xyz, w):
                u[offset:offset+4] = [*(np.array(xyz)+shift-eye), w]
            if kind.startswith('ram'):
                hit = kind == 'ram-hit'
                body = np.array([0, 0, min(age*9, 2.5)])
                point(bounds, body, 1.8)
                point(bounds+4, [0, .95, 2.6], 1.42)
                point(bounds+8, [0, 0, 2.8], 1.8)
                for j in range(5): point(bounds+12+j*4, body-[0, 0, (j+1)*.25], 1.8)
                def smooth(a, b, t):
                    v = np.clip((t-a)/(b-a), 0, 1)
                    return v*v*(3-2*v)
                flash = float(hit and age >= .1)*(1-smooth(.1, .23, age))
                suppression = float(hit)*smooth(.1, .14, age)*(1-smooth(.74, 1.08, age))
                u[style:style+4] = [1, age, 9, 1]
                u[material:material+12] = [0, 0, 1, 1, 1.8, hit, flash, suppression, 0, 0, -.2, .6]
            else:
                off = .6 if kind == 'bulwark-release' else 4
                t = np.clip((age-off)/.62, 0, 1)
                alpha = (1-t*t*(3-2*t))*min(1, age/.045)
                u[style:style+4] = [alpha, age, 11 if kind == 'bulwark-inside' else 10, 1]
                point(bounds, [0, 0, 0], 1.8)
                u[material:material+9] = [1, .7, alpha, 0, 1, 0, 0, .08, off]
            continue
        if kind == 'quake' and size == 15136:
            ground_channels(u, eye, age, quality, i)
        if kind == 'burst' and size > 300:
            for j in range(4+quality*4):
                seed = j*3 if quality == 0 else (j*5)%12 if quality == 1 else j
                variation = lambda k: .5+.5*math.sin(k*7.13+1.7)
                speed = 11+8*variation(seed); life = .65+.5*variation(seed+4)
                if age >= life: continue
                t = age/life; alpha = min(1, age/.025)*(1-t*t*(3-2*t))
                y = 1-(seed+.5)/6; angle = seed*2.3999632297+42*.6180339887
                direction = np.array([math.cos(angle)*math.sqrt(1-y*y), y, math.sin(angle)*math.sqrt(1-y*y)])
                reach = speed/3.8*(1-math.exp(-3.8*age))
                tail = reach-speed/3.8*(1-math.exp(-3.8*max(0, age-.035)))
                stride = 32 if size == 15136 else 12
                entry = i*stride+j; offset = 72+entry*4
                u[offset:offset+4] = [*(origin+direction*reach), (.025+.027*variation(seed+7))*(.7+alpha*.3)]
                offset += 4*stride*4
                u[offset:offset+4] = [*(-direction*tail), alpha]
    return u.tobytes()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path)
    parser.add_argument('--output', type=Path, default=Path('/tmp/opencode/flake-vfx-probe'))
    parser.add_argument('--width', type=int, default=1280)
    parser.add_argument('--height', type=int, default=720)
    parser.add_argument('--samples', type=int, default=40)
    parser.add_argument('--bastion', action='store_true', help='Time Bastion instead of ground lightning')
    parser.add_argument('--ghoul-fixtures', type=Path, help='Directory with captured ghoul-{aura,lunge,siphon}.ubo and camera .json files')
    args = parser.parse_args(); args.output.mkdir(parents=True, exist_ok=True)
    fixtures = {name: (np.fromfile(args.ghoul_fixtures/(name+'.ubo'), dtype='<f4'),
                        np.array(json.loads((args.ghoul_fixtures/(name+'.json')).read_text())))
                for name in ('ghoul-aura', 'ghoul-lunge', 'ghoul-siphon')} if args.ghoul_fixtures else None
    ctx = moderngl.create_standalone_context(require=330, backend='egl')
    print(ctx.info['GL_RENDERER'])
    size = args.width, args.height
    color = ctx.texture(size, 4); depth = ctx.texture(size, 1, dtype='f4')
    scene = ctx.framebuffer([color, depth]); output = ctx.simple_framebuffer(size)
    scene_program = ctx.program(vertex_shader=VERTEX, fragment_shader=SCENE)
    scene_vao = ctx.vertex_array(scene_program, [])
    color.filter = (moderngl.LINEAR, moderngl.LINEAR)
    color.repeat_x = color.repeat_y = depth.repeat_x = depth.repeat_y = False
    color.use(0); depth.use(1)
    current = Path(__file__).resolve().parents[1] / 'src/client/resources/assets/flake/shaders/post/shockwave.fsh'
    paths = [('before', args.baseline)] if args.baseline else []
    paths.append(('after', current))
    results = {'renderer': ctx.info['GL_RENDERER'], 'resolution': size, 'samples': args.samples, 'measurements': {}}
    for label, path in paths:
        program = ctx.program(vertex_shader=VERTEX, fragment_shader=path.read_text())
        vao = ctx.vertex_array(program, []); block = program['ShockwaveConfig']; block.binding = 0
        buffer = ctx.buffer(reserve=block.size); buffer.bind_to_uniform_block(0)
        program['InSampler'] = 0; program['DepthSampler'] = 1
        def prepare(eye, target, wall=0):
            eye = np.array(eye, dtype=float); vp, inv = matrices(eye, target, args.width/args.height)
            scene_program['vp'].write(vp.T.astype('f4').tobytes())
            scene_program['inv'].write(inv.T.astype('f4').tobytes())
            scene_program['eye'].value = tuple(eye); scene_program['wall'].value = wall
            scene.use(); scene_vao.render(vertices=3); output.use()
            return eye, vp, inv
        eye, vp, inv = prepare([6, 3, 7], [0, 1, 4.5])
        for quality in ([0, 1, 2] if label == 'after' else [2]):
            kinds = tuple(fixtures) if fixtures else ('ram-hit', 'bulwark') if args.bastion else ('quake',)
            for count, kind in itertools.product((1, 4), kinds):
                key = f'{label}/{kind}/q{quality}/waves{count}'
                for _ in range(12):
                    buffer.write(uniforms(block.size, vp, inv, eye, kind, .3, quality, count, fixtures)); vao.render(vertices=3)
                ctx.finish(); timings = []
                for sample in range(args.samples):
                    age = .04 + .65 * sample / max(1, args.samples-1)
                    buffer.write(uniforms(block.size, vp, inv, eye, kind, age, quality, count, fixtures))
                    with ctx.query(time=True) as query: vao.render(vertices=3)
                    timings.append(query.elapsed / 1e6)
                result = {'median_ms': float(np.median(timings)), 'p95_ms': float(np.percentile(timings, 95))}
                results['measurements'][key] = result
                print(key, result)
        if label == 'after':
            # Functional checks against the actual driver, not just shader syntax.
            eye, vp, inv = prepare([0, 2, -5], [0, 1, 4.5], 1)
            empty = np.zeros(block.size // 4, dtype='f4').tobytes()
            buffer.write(empty); vao.render(vertices=3)
            reference = np.frombuffer(output.read(components=3), dtype='u1').astype('i2')
            buffer.write(uniforms(block.size, vp, inv, eye, 'quake', .4)); vao.render(vertices=3)
            occluded = np.frombuffer(output.read(components=3), dtype='u1').astype('i2')
            assert np.max(np.abs(reference - occluded)) <= 1, 'Wave must not shine through opaque wall'
            eye, vp, inv = prepare([7, 2, 5], [0, 1, 5])
            buffer.write(empty); vao.render(vertices=3)
            reference = np.frombuffer(output.read(components=3), dtype='u1').reshape(args.height, args.width, 3).astype('i2')
            buffer.write(uniforms(block.size, vp, inv, eye, 'quake', .4, 0)); vao.render(vertices=3)
            low = np.frombuffer(output.read(components=3), dtype='u1').reshape(args.height, args.width, 3).astype('i2')
            ys, xs = np.where(np.max(np.abs(reference - low), axis=2) > 25)
            assert len(xs) > 50 and np.ptp(xs) > args.width * .08, 'Low must retain visible ground lightning'
            results['checks'] = {'opaque_wall_occlusion': 'passed', 'low_ground_channels': 'passed'}
            for kind in ('ram-hit', 'bulwark'):
                eye, vp, inv = prepare([0, 2, 6], [0, 1, 0], 3 if kind == 'ram-hit' else 2)
                buffer.write(empty); vao.render(vertices=3)
                reference = np.frombuffer(output.read(components=3), dtype='u1').astype('i2')
                buffer.write(uniforms(block.size, vp, inv, eye, kind, .16 if kind == 'ram-hit' else 1))
                vao.render(vertices=3)
                tested = np.frombuffer(output.read(components=3), dtype='u1').astype('i2')
                assert np.max(np.abs(reference-tested)) <= 1, kind+' shone through wall'
                results['checks'][kind+'_wall_occlusion'] = 'passed'
            eye, vp, inv = prepare([4, 2, 4], [0, 1, 0])
            for kind, age in [('bulwark', 1), ('ram-miss', .65)]:
                buffer.write(empty); vao.render(vertices=3)
                reference = np.frombuffer(output.read(components=3), dtype='u1').astype('i2')
                buffer.write(uniforms(block.size, vp, inv, eye, kind, age, 0)); vao.render(vertices=3)
                rendered = np.frombuffer(output.read(components=3), dtype='u1').astype('i2')
                if kind == 'ram-miss':
                    assert np.max(np.abs(reference-rendered)) <= 1, 'Miss left fictional impact/suppression'
                else:
                    assert np.count_nonzero(np.abs(reference-rendered)>8) > 50, 'Low lost fortress armour'
            results['checks']['ram_miss_no_impact_or_suppression'] = 'passed'
            results['checks']['bulwark_low_visible'] = 'passed'
            if fixtures:
                for kind in fixtures:
                    eye, vp, inv = prepare([0,2,6], [0,1,0], 3)
                    buffer.write(empty); vao.render(vertices=3)
                    reference = np.frombuffer(output.read(components=3), dtype='u1').astype('i2')
                    buffer.write(uniforms(block.size, vp, inv, eye, kind, .3, fixtures=fixtures)); vao.render(vertices=3)
                    rendered = np.frombuffer(output.read(components=3), dtype='u1').astype('i2')
                    assert np.max(np.abs(reference-rendered)) <= 1, kind+' shone through wall'
                    results['checks'][kind+'_wall_occlusion'] = 'passed'
            print('GPU checks passed: '+', '.join(results['checks']))
            for name, eye_pos, target, kind, age, quality, wall in [
                ('quake-side-low', [7,2,5], [0,1,5], 'quake', .4, 0, 0),
                ('quake-side-high', [7,2,5], [0,1,5], 'quake', .4, 2, 0),
                ('quake-front', [0,2,12], [0,1,4], 'quake', .4, 2, 0),
                ('quake-near', [6,3,7], [0,1,4.5], 'quake', .13, 2, 0),
                ('quake-far', [6,3,7], [0,1,4.5], 'quake', .5, 2, 0),
                ('quake-occluded', [0,2,-5], [0,1,4.5], 'quake', .4, 2, 1),
                ('charge', [3,2,4], [0,1,0], 'charge', .4, 2, 0),
                ('ejecta', [5,3,6], [0,1,0], 'burst', .28, 2, 0),
                ('impact', [6,3,7], [0,1,4.5], 'impact', .06, 2, 0),
                ('bulwark-assembly', [3,2,4], [0,1,0], 'bulwark', .12, 2, 0),
                ('bulwark-front', [0,2,4], [0,1,0], 'bulwark', 1, 2, 0),
                ('bulwark-side', [4,2,0], [0,1,0], 'bulwark', 1, 2, 0),
                ('bulwark-inside', [0,1.62,0], [0,1.4,3], 'bulwark-inside', 1, 2, 0),
                ('bulwark-release', [3,2,4], [0,1,0], 'bulwark-release', .9, 2, 0),
                ('ram-miss', [3,2,4], [0,1,1], 'ram-miss', .16, 2, 0),
                ('ram-hit', [3,2,4], [0,1,2], 'ram-hit', .17, 2, 0),
            ]:
                eye, vp, inv = prepare(eye_pos, target, wall)
                buffer.write(uniforms(block.size, vp, inv, eye, kind, age, quality)); vao.render(vertices=3)
                image = Image.frombytes('RGB', size, output.read(components=3)).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
                image.save(args.output / f'{name}.png')
            if fixtures:
                for kind in fixtures:
                    for angle, pos in [('side',[4,2,1]), ('front',[0,2,5]), ('rear',[0,2,-5])]:
                        eye, vp, inv = prepare(pos, [0,1,0])
                        buffer.write(uniforms(block.size, vp, inv, eye, kind, .3, fixtures=fixtures)); vao.render(vertices=3)
                        Image.frombytes('RGB', size, output.read(components=3)).transpose(Image.Transpose.FLIP_TOP_BOTTOM).save(args.output/(kind+'-'+angle+'.png'))
        buffer.release(); vao.release(); program.release()
    (args.output / 'timings.json').write_text(json.dumps(results, indent=2) + '\n')
    ctx.release()


if __name__ == '__main__':
    main()

package dev.flake.runtime;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Owns runtime state for one server lifetime, never for the lifetime of the JVM. */
public final class ServerState<T> {
	private final Map<MinecraftServer, T> instances = new IdentityHashMap<>();
	private final Supplier<T> factory;

	public ServerState(Supplier<T> factory) {
		this.factory = factory;
		ServerLifecycleEvents.SERVER_STOPPED.register(instances::remove);
	}

	public T of(MinecraftServer server) { return instances.computeIfAbsent(server, ignored -> factory.get()); }
	public T of(ServerPlayer player) { return of(player.level().getServer()); }
	public T existing(MinecraftServer server) { return instances.get(server); }
	public void remove(MinecraftServer server) { instances.remove(server); }
	public java.util.Collection<T> values() { return java.util.List.copyOf(instances.values()); }
}

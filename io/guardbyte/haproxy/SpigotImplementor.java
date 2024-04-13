package io.guardbyte.haproxy;

import io.guardbyte.haproxy.tinyprotocol.TinyProtocol;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class SpigotImplementor extends JavaPlugin {
    @Override
    public void onEnable() {
        new TinyProtocol(this);
        getLogger().info("Guardbyte protection has been activated.");
    }
    @Override
    public void onDisable() {
        getLogger().info("Guardbyte protection has been deactivated.");
    }
}

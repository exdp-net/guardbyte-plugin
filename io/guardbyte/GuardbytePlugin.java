package io.guardbyte;

import org.bukkit.plugin.java.JavaPlugin;

public class GuardbytePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        new ProtocolHandler(this);
        getLogger().info("---------------------------------------------------------");
        getLogger().info("| Guardbyte.io has been activated, you're safe with me. |");
        getLogger().info("---------------------------------------------------------");

    }
    @Override
    public void onDisable() {
        getLogger().info("----------------------------------------------------");
        getLogger().info("| Guardbyte.io has been deactivated, see you soon! |");
        getLogger().info("----------------------------------------------------");
    }
}

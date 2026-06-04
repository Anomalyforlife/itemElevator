package it.anomalyforlife.itemelevators.config;

import it.anomalyforlife.itemelevators.ItemElevators;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LangManager {

    private final ItemElevators plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private FileConfiguration lang;

    public LangManager(ItemElevators plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String language = plugin.getConfigManager().getLanguage();
        String resourcePath = "lang/" + language + ".yml";

        File langFile = new File(plugin.getDataFolder(), resourcePath);
        if (!langFile.exists()) {
            // Try to save bundled resource; fall back to English if missing
            InputStream bundled = plugin.getResource(resourcePath);
            if (bundled != null) {
                plugin.saveResource(resourcePath, false);
            } else {
                plugin.getLogger().warning("Language file '" + resourcePath + "' not found, falling back to en.yml.");
                resourcePath = "lang/en.yml";
                langFile = new File(plugin.getDataFolder(), resourcePath);
                if (!langFile.exists()) {
                    plugin.saveResource(resourcePath, false);
                }
            }
        }

        lang = YamlConfiguration.loadConfiguration(langFile);

        // Merge with defaults from the jar so missing keys still resolve
        InputStream defaults = plugin.getResource(resourcePath);
        if (defaults == null) defaults = plugin.getResource("lang/en.yml");
        if (defaults != null) {
            YamlConfiguration defaultLang = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaults, StandardCharsets.UTF_8));
            lang.setDefaults(defaultLang);
        }
    }

    public String getRaw(String key) {
        String value = lang.getString(key);
        if (value == null) {
            plugin.getLogger().warning("Missing lang key: " + key);
            return "<red>[MISSING: " + key + "]</red>";
        }
        // Resolve nested {prefix} placeholder
        String prefix = lang.getString("prefix", "");
        return value.replace("<prefix>", prefix);
    }

    public Component get(String key) {
        return mm.deserialize(getRaw(key));
    }

    public Component get(String key, String... placeholders) {
        String raw = getRaw(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace(placeholders[i], placeholders[i + 1]);
        }
        return mm.deserialize(raw);
    }

    public void send(Audience audience, String key, String... placeholders) {
        if (audience == null) return;
        audience.sendMessage(get(key, placeholders));
    }
}

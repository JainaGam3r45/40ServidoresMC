package com.cadiducho.cservidoresmc.config;

import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interfaz que cualquier tipo de servidor tendrá que implementar y devolver los datos correctos de configuración
 */
public interface CSConfiguration {

    /**
     * Recargar la configuración
     */
    void reload();

    /**
     * Devuelve la lista de comandos custom
     * @return Lista de comandos custom
     */
    default List<String> customCommandsList() {
        return getStringList("rewards.commands", "comandosCustom", new ArrayList<>());
    }

    /**
     * Devuelve el tag del plugin para votación
     * @return El tag del plugin
     */
    default String getTag() {
        return getString("messages.prefix", "tag", "&8[&b40ServidoresMC&8]");
    }

    /**
     * Devuelve una string de la configuración
     * @param key La clave
     * @param defValue El valor por defecto
     * @return El valor de la clave en la configuración
     */
    String getString(String key, String defValue);

    default String getString(String key, String legacyKey, String defValue) {
        String value = getString(key, null);
        if (value != null) {
            return value;
        }
        return getString(legacyKey, defValue);
    }

    /**
     * Devuelve una string de la configuración
     * @param key La clave
     * @return El valor de la clave en la configuración
     */
    default String getString(String key) {
        return getString(key, "");
    }

    /**
     * Devuelve un int de la configuración
     * @param key La clave
     * @param defValue El valor por defecto
     * @return El valor de la clave en la configuración
     */
    int getInt(String key, int defValue);

    default int getInt(String key, String legacyKey, int defValue) {
        int sentinel = Integer.MIN_VALUE;
        int value = getInt(key, sentinel);
        if (value != sentinel) {
            return value;
        }
        return getInt(legacyKey, defValue);
    }

    /**
     * Devuelve un int de la configuración
     * @param key La clave
     * @return El valor de la clave en la configuración
     */
    default int getInt(String key) {
        return getInt(key, 0);
    }

    /**
     * Devuelve un boolean de la configuración
     * @param key La clave
     * @param defValue El valor por defecto
     * @return El valor de la clave en la configuración
     */
    boolean getBoolean(String key, boolean defValue);

    default boolean getBoolean(String key, String legacyKey, boolean defValue) {
        String value = getString(key, null);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return getBoolean(legacyKey, defValue);
    }

    /**
     * Devuelve un boolean de la configuración
     * @param key La clave
     * @return El valor de la clave en la configuración
     */
    default boolean getBoolean(String key) {
        return getBoolean(key, true);
    }

    /**
     * Devuelve una lista de strings de la configuración
     * @param key La clave
     * @return El valor de la clave en la configuración
     */
    default List<String> getStringList(String key) {
        return getStringList(key, new ArrayList<>());
    }

    /**
     * Devuelve una lista de strings de la configuración
     * @param path La clave
     * @param def El valor por defecto
     * @return El valor de la clave en la configuración
     */
    List<String> getStringList(String path, List<String> def);

    default List<String> getStringList(String path, String legacyPath, List<String> def) {
        List<String> value = getStringList(path, null);
        if (value != null) {
            return value;
        }
        return getStringList(legacyPath, def);
    }

    /**
     * Devuelve un mapa de strings de la configuración
     * @param path La clave
     * @return El valor de la clave en la configuración
     */
    default Map<String, String> getStringMap(String path) {
        return getStringMap(path, new HashMap<>());
    }

    /**
     * Devuelve un mapa de strings de la configuración
     * @param path La clave
     * @param def El valor por defecto
     * @return El valor de la clave en la configuración
     */
    Map<String, String> getStringMap(String path, Map<String, String> def);

    /**
     * Devuelve un mapa de listas de strings de la configuración
     * @param path La clave
     * @return El valor de la clave en la configuración
     */
    default Map<String, List<String>> getStringListMap(String path) {
        return getStringListMap(path, new HashMap<>());
    }

    /**
     * Devuelve un mapa de listas de strings de la configuración
     * @param path La clave
     * @param def El valor por defecto
     * @return El valor de la clave en la configuración
     */
    default Map<String, List<String>> getStringListMap(String path, Map<String, List<String>> def) {
        return def;
    }

    /**
     * Devuelve el plugin de CServidoresMC
     * @return El plugin
     */
    CSPlugin getPlugin();
}

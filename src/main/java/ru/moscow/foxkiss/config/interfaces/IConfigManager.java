package ru.moscow.foxkiss.config.interfaces;

import ru.moscow.foxkiss.config.ConfigValues;

public interface IConfigManager {

    void reload();

    ConfigValues getConfigValues();
}

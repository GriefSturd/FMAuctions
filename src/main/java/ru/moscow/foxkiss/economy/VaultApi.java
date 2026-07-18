package ru.moscow.foxkiss.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultApi {

    private final Economy economy;

    public VaultApi() {
        RegisteredServiceProvider<Economy> provider =
                Bukkit.getServicesManager().getRegistration(Economy.class);

        if (provider == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }

        this.economy = provider.getProvider();

        if (economy == null) {
            throw new IllegalStateException("Vault economy is unavailable");
        }
    }

    public boolean has(Player player, double amount) {
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public void deposit(OfflinePlayer player, double amount) {
        economy.depositPlayer(player, amount);
    }
}
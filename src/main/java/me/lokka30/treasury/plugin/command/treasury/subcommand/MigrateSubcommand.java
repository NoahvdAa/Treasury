/*
 * Copyright (c) 2021-2021 lokka30.
 *
 * This code is part of Treasury, an Economy API for Minecraft servers. Please see <https://github.com/lokka30/Treasury> for more information on this resource.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package me.lokka30.treasury.plugin.command.treasury.subcommand;

import me.lokka30.microlib.maths.QuickTimer;
import me.lokka30.microlib.messaging.MultiMessage;
import me.lokka30.treasury.api.economy.EconomyProvider;
import me.lokka30.treasury.api.economy.currency.Currency;
import me.lokka30.treasury.api.economy.exception.*;
import me.lokka30.treasury.plugin.Treasury;
import me.lokka30.treasury.plugin.command.Subcommand;
import me.lokka30.treasury.plugin.debug.DebugCategory;
import me.lokka30.treasury.plugin.misc.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MigrateSubcommand implements Subcommand {

    /*
    inf: Migrates accounts from one economy plugin to another
    cmd: /treasury migrate <providerFrom> <providerTo>
    arg:         |       0              1            2
    len:         0       1              2            3
     */

    @NotNull private final Treasury main;
    public MigrateSubcommand(@NotNull final Treasury main) { this.main = main; }

    @Override
    public void run(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        final boolean debugEnabled = main.debugHandler.isCategoryEnabled(DebugCategory.MIGRATE_SUBCOMMAND);

        if(!Utils.checkPermissionForCommand(main, sender, "treasury.command.treasury.migrate")) return;

        if(args.length != 3) {
            new MultiMessage(main.messagesCfg.getConfig().getStringList("commands.treasury.subcommands.migrate.invalid-usage"), Arrays.asList(
                    new MultiMessage.Placeholder("prefix", main.messagesCfg.getConfig().getString("common.prefix"), true),
                    new MultiMessage.Placeholder("label", label, false)
            ));
            return;
        }

        Collection<RegisteredServiceProvider<EconomyProvider>> serviceProviders = main.getServer().getServicesManager().getRegistrations(EconomyProvider.class);
        EconomyProvider from = null;
        EconomyProvider to = null;

        if(serviceProviders.size() < 2) {
            new MultiMessage(main.messagesCfg.getConfig().getStringList("commands.treasury.subcommands.migrate.requires-two-providers"), Collections.singletonList(
                    new MultiMessage.Placeholder("prefix", main.messagesCfg.getConfig().getString("common.prefix"), true)
            ));
            return;
        }

        final HashSet<String> serviceProvidersNames = new HashSet<>();

        for(RegisteredServiceProvider<EconomyProvider> serviceProvider : serviceProviders) {
            serviceProvidersNames.add(serviceProvider.getPlugin().getName());
            if(debugEnabled) {
                main.debugHandler.log(DebugCategory.MIGRATE_SUBCOMMAND, "Found service provider: " + serviceProvider.getPlugin().getName());
            }
        }

        if(args[1].equalsIgnoreCase(args[2])) {
            new MultiMessage(main.messagesCfg.getConfig().getStringList("commands.treasury.subcommands.migrate.providers-match"), Arrays.asList(
                    new MultiMessage.Placeholder("prefix", main.messagesCfg.getConfig().getString("common.prefix"), true),
                    new MultiMessage.Placeholder("providers", Utils.formatListMessage(main, new ArrayList<>(serviceProvidersNames)), false)
            ));
            return;
        }

        for(RegisteredServiceProvider<EconomyProvider> serviceProvider : serviceProviders) {
            final String serviceProviderPluginName = serviceProvider.getPlugin().getName();

            if(args[1].equalsIgnoreCase(serviceProviderPluginName)) {
                from = serviceProvider.getProvider();
            } else if(args[2].equalsIgnoreCase(serviceProviderPluginName)) {
                to = serviceProvider.getProvider();
            }
        }

        if(from == null) {
            new MultiMessage(main.messagesCfg.getConfig().getStringList("commands.treasury.subcommands.migrate.requires-valid-from"), Arrays.asList(
                    new MultiMessage.Placeholder("prefix", main.messagesCfg.getConfig().getString("common.prefix"), true),
                    new MultiMessage.Placeholder("providers", Utils.formatListMessage(main, new ArrayList<>(serviceProvidersNames)), false)
            ));
            return;
        }

        if(to == null) {
            new MultiMessage(main.messagesCfg.getConfig().getStringList("commands.treasury.subcommands.migrate.requires-valid-to"), Arrays.asList(
                    new MultiMessage.Placeholder("prefix", main.messagesCfg.getConfig().getString("common.prefix"), true),
                    new MultiMessage.Placeholder("providers", Utils.formatListMessage(main, new ArrayList<>(serviceProvidersNames)), false)
            ));
            return;
        }

        if(debugEnabled) {
            main.debugHandler.log(DebugCategory.MIGRATE_SUBCOMMAND, "Migrating from '&b" + from.getProvider().getName() + "&7' to '&b" + to.getProvider().getName() + "&7'.");
        }

        new MultiMessage(main.messagesCfg.getConfig().getStringList("commands.treasury.subcommands.migrate.starting-migration"), Collections.singletonList(
                new MultiMessage.Placeholder("prefix", main.messagesCfg.getConfig().getString("common.prefix"), true)
        ));

        final QuickTimer timer = new QuickTimer();

        int playerAccountsProcessed = 0;
        int bankAccountsProcessed = 0;

        HashMap<String, Currency> migratedCurrencies = new HashMap<>();
        HashSet<String> nonMigratedCurrencies = new HashSet<>();

        for(String currencyId : from.getCurrencyNames()) {
            if(to.getCurrencyNames().contains(currencyId)) {
                try {
                    migratedCurrencies.put(currencyId, to.getCurrency(currencyId));
                } catch(InvalidCurrencyException ex) {
                    // this should be impossible
                    ex.printStackTrace();
                    new MultiMessage(main.messagesCfg.getConfig().getStringList("commands.treasury.subcommands.migrate.internal-error"), Collections.singletonList(
                            new MultiMessage.Placeholder("prefix", main.messagesCfg.getConfig().getString("common.prefix"), true)
                    ));
                    continue;
                }

                if(debugEnabled) {
                    main.debugHandler.log(DebugCategory.MIGRATE_SUBCOMMAND, "Currency of ID '&b" + currencyId + "&7' will be migrated.");
                }
            } else {
                nonMigratedCurrencies.add(currencyId);

                if(debugEnabled) {
                    main.debugHandler.log(DebugCategory.MIGRATE_SUBCOMMAND, "Currency of ID '&b" + currencyId + "&7' will not be migrated.");
                }
            }
        }

        /* Migrate player accounts */
        try {
            for(UUID uuid : from.getPlayerAccountIds()) {
                if(debugEnabled) main.debugHandler.log(DebugCategory.MIGRATE_SUBCOMMAND, "Migrating player account of UUID '&b" + uuid + "&7'.");

                if(!to.hasPlayerAccount(uuid)) {
                    to.createPlayerAccount(uuid);
                }

                for(String currencyId : migratedCurrencies.keySet()) {
                    final double balance = Utils.ensureAtLeastZero(from.getPlayerAccount(uuid).getBalance(null, from.getCurrency(currencyId)));

                    from.getPlayerAccount(uuid).withdrawBalance(balance, null, from.getCurrency(currencyId));
                    to.getPlayerAccount(uuid).depositBalance(balance, null, to.getCurrency(currencyId));
                }

                playerAccountsProcessed++;
            }
        } catch(AccountAlreadyExistsException | InvalidCurrencyException | NegativeAmountException | OversizedWithdrawalException ex) {
            // these should be impossible
            ex.printStackTrace();
            new MultiMessage(main.messagesCfg.getConfig().getStringList("commands.treasury.subcommands.migrate.internal-error"), Collections.singletonList(
                    new MultiMessage.Placeholder("prefix", main.messagesCfg.getConfig().getString("common.prefix"), true)
            ));
            return;
        }

        /* Migrate bank accounts */
        if(from.hasBankAccountSupport() && to.hasBankAccountSupport()) {
            try {
                for(UUID uuid : from.getBankAccountIds()) {
                    if(debugEnabled) main.debugHandler.log(DebugCategory.MIGRATE_SUBCOMMAND, "Migrating bank account of UUID '&b" + uuid + "&7'.");

                    if(!to.hasBankAccount(uuid)) {
                        to.createBankAccount(uuid);

                        for(UUID ownerId : from.getBankAccount(uuid).getBankOwnersIds()) {
                            to.getBankAccount(uuid).addBankOwner(ownerId);
                        }

                        for(UUID memberId : from.getBankAccount(uuid).getBankMembersIds()) {
                            to.getBankAccount(uuid).addBankMember(memberId);
                        }
                    }

                    for(String currencyId : migratedCurrencies.keySet()) {
                        final double balance = Utils.ensureAtLeastZero(from.getBankAccount(uuid).getBalance(null, from.getCurrency(currencyId)));

                        from.getBankAccount(uuid).withdrawBalance(balance, null, from.getCurrency(currencyId));
                        to.getBankAccount(uuid).depositBalance(balance, null, to.getCurrency(currencyId));
                    }

                    bankAccountsProcessed++;
                }
            } catch(AccountAlreadyExistsException | UnsupportedEconomyFeatureException | InvalidCurrencyException | NegativeAmountException | OversizedWithdrawalException ex) {
                // these should be impossible
                ex.printStackTrace();
                new MultiMessage(main.messagesCfg.getConfig().getStringList("commands.treasury.subcommands.migrate.internal-error"), Collections.singletonList(
                        new MultiMessage.Placeholder("prefix", main.messagesCfg.getConfig().getString("common.prefix"), true)
                ));
                return;
            }
        }

        new MultiMessage(main.messagesCfg.getConfig().getStringList("commands.treasury.subcommands.migrate.finished-migration"), Arrays.asList(
                new MultiMessage.Placeholder("prefix", main.messagesCfg.getConfig().getString("common.prefix"), true),
                new MultiMessage.Placeholder("time", timer.getTimer() + "", false),
                new MultiMessage.Placeholder("player-accounts", playerAccountsProcessed + "", false),
                new MultiMessage.Placeholder("bank-accounts", bankAccountsProcessed + "", false),
                new MultiMessage.Placeholder("migrated-currencies", Utils.formatListMessage(main, new ArrayList<>(migratedCurrencies.keySet())), false),
                new MultiMessage.Placeholder("non-migrated-currencies", Utils.formatListMessage(main, new ArrayList<>(nonMigratedCurrencies)), false)
        ));
    }
}

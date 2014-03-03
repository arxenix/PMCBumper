package com.ksanur.pmcbumper;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import mondocommand.CallInfo;
import mondocommand.MondoCommand;
import mondocommand.dynamic.Sub;
import org.apache.commons.logging.LogFactory;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: bobacadodl
 * Date: 3/2/14
 * Time: 9:22 PM
 */
public class PMCBumper extends JavaPlugin {
    WebClient webClient;

    public void onEnable() {
        saveDefaultConfig();
        enableWebClient();

        MondoCommand pmcbump = new MondoCommand();
        pmcbump.autoRegisterFrom(this);
        getCommand("pmcbump").setExecutor(pmcbump);

        if (getConfig().getBoolean("auto-bump")) {
            new BukkitRunnable() {
                public void run() {
                    if (getConfig().getStringList("pmc.server-pages").size() > 0) {
                        if (shouldBump()) {
                            getLogger().info("24 hours since last bump! Attempting to bump server now!");
                            getLogger().info("Logging in...");
                            if (tryLogin()) {
                                getLogger().info("Logged in!");
                                for (String serverPage : getConfig().getStringList("pmc.server-pages")) {
                                    getLogger().info("Bumping server...");
                                    if (tryBump(serverPage)) {
                                        getLogger().info("Bumped server!");
                                    } else {
                                        getLogger().info("Could not bump server!");
                                    }
                                }
                            } else {
                                getLogger().severe("Failed to login!");
                            }
                        }
                    }
                }
            }.runTaskTimerAsynchronously(this, 10L, TimeUnit.MINUTES.toSeconds(5) * 20);
        }
    }


    @Sub(name = "setlogin", usage = "<email> <password", description = "Setup your PMC account login",
            permission = "pmcbump.setlogin", minArgs = 2)
    public void setLogin(CallInfo call) {
        getConfig().set("pmc.email", call.getArg(0));
        getConfig().set("pmc.password", call.getArg(1));
        saveConfig();

        sendMessage(call.getSender(), "&aSuccessfully updated login credentials!");
    }

    @Sub(name = "addserver", usage = "<pmc server page url>", description = "Add your PMC server page",
            permission = "pmcbump.addserver", minArgs = 1)
    public void addServer(CallInfo call) {
        List<String> pages = getConfig().getStringList("pmc.server-pages");
        pages.add(call.getArg(0));
        getConfig().set("pmc.server-pages", pages);
        saveConfig();

        sendMessage(call.getSender(), "&aSuccessfully added server page!");
    }

    @Sub(name = "bump", description = "Bump your PMC servers",
            permission = "pmcbump.bump")
    public void bump(final CallInfo call) {
        if (getConfig().getStringList("pmc.server-pages").size() > 0) {
            if (shouldBump()) {
                sendMessage(call.getSender(), "&aLogging in...");
                new BukkitRunnable() {
                    public void run() {
                        if (tryLogin()) {
                            sendMessage(call.getSender(), "&aLogged in!");
                            for (String serverPage : getConfig().getStringList("pmc.server-pages")) {
                                sendMessage(call.getSender(), "&aAttempting to bump "+serverPage);
                                if (tryBump(serverPage)) {
                                    sendMessage(call.getSender(), "&aServer bumped!");
                                } else {
                                    sendMessage(call.getSender(), "&aFailed to bump server!");
                                }
                            }
                        } else {
                            sendMessage(call.getSender(), "&cFailed to login!");
                        }
                    }
                }.runTaskAsynchronously(this);
            } else {
                sendMessage(call.getSender(), "&cServers have already been bumped in the past 24 hours!");
            }
        }
        else {
            sendMessage(call.getSender(), "&cYou have no server pages!");
        }
    }


    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GREEN + "[" + ChatColor.DARK_AQUA + "PMCBump" + ChatColor.GREEN + "]" + ChatColor.WHITE + " " + ChatColor.translateAlternateColorCodes('&', message));
    }

    private boolean shouldBump() {
        return TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis()) - TimeUnit.MILLISECONDS.toHours(getConfig().getLong("last-bump")) >= 24;
    }

    private boolean tryBump(String serverPage) {
        String serverId = serverPage.replaceAll("\\D+", "");

        HtmlPage page;
        try {
            page = webClient.getPage("http://www.planetminecraft.com/account/manage/servers/" + serverId + "/#tab_log");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        try {
            HtmlElement bumpElement = (HtmlElement) page.getElementById("bump");
            bumpElement.click();
        } catch (Exception e) {
            return false;
        }

        getConfig().set("last-bump", System.currentTimeMillis());
        saveConfig();
        return true;
    }

    private boolean tryLogin() {
        HtmlPage page;
        try {
            page = webClient.getPage("http://www.planetminecraft.com/account/sign_in/");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if (page.getTitleText().matches(".*Website is currently unreachable.*")) {
            getLogger().severe("PMC is currently offline!");
            return false;
        }

        try {
            HtmlForm form = page.getFirstByXPath("/html/body//div[@class='half']/form");
            HtmlElement usernameElement = form.getInputByName("username");
            HtmlElement passwordElement = form.getInputByName("password");
            HtmlElement loginElement = form.getInputByName("login");

            usernameElement.type(getConfig().getString("pmc.email"));
            passwordElement.type(getConfig().getString("pmc.password"));
            page = loginElement.click();
        } catch (Exception e) {
            e.printStackTrace();
        }

        HtmlElement errorElement = page.getFirstByXPath("/html/body//div[@class='error']");
        if (errorElement != null) {
            getLogger().severe("Login to PMC failed! " + errorElement.getTextContent());
            return false;
        }
        return true;
    }

    private void enableWebClient() {
        webClient = new WebClient(BrowserVersion.CHROME);
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setCssEnabled(true);
        webClient.getOptions().setRedirectEnabled(true);
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
        Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
    }
}

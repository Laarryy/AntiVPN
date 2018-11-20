package me.egg82.antivpn.events;

import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import me.egg82.antivpn.VPNAPI;
import me.egg82.antivpn.extended.CachedConfigValues;
import me.egg82.antivpn.extended.Configuration;
import me.egg82.antivpn.utils.LogUtil;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostLoginCheckHandler implements Consumer<PostLoginEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ProxyServer proxy;

    private final VPNAPI api = VPNAPI.getInstance();

    public PostLoginCheckHandler(ProxyServer proxy) {
        this.proxy = proxy;
    }

    public void accept(PostLoginEvent event) {
        String ip = getIp(event.getPlayer().getRemoteAddress());
        if (ip == null || ip.isEmpty()) {
            return;
        }

        Configuration config;
        CachedConfigValues cachedConfig;

        try {
            config = ServiceLocator.get(Configuration.class);
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        if (event.getPlayer().hasPermission("avpn.bypass")) {
            if (cachedConfig.getDebug()) {
                proxy.getConsoleCommandSource().sendMessage(LogUtil.getHeading().append(TextComponent.of(event.getPlayer().getUsername()).color(TextColor.WHITE)).append(TextComponent.of(" bypasses check. Ignoring.").color(TextColor.YELLOW)).build());
            }
            return;
        }

        if (!config.getNode("kick", "enabled").getBoolean(true)) {
            if (cachedConfig.getDebug()) {
                proxy.getConsoleCommandSource().sendMessage(LogUtil.getHeading().append(TextComponent.of("Plugin set to API-only. Ignoring ").color(TextColor.YELLOW)).append(TextComponent.of(event.getPlayer().getUsername()).color(TextColor.WHITE)).build());
            }
            return;
        }

        if (cachedConfig.getIgnoredIps().contains(ip)) {
            return;
        }

        boolean isVPN;

        if (config.getNode("kick", "algorithm", "method").getString("cascade").equalsIgnoreCase("consensus")) {
            double consensus = clamp(0.0d, 1.0d, config.getNode("kick", "algorithm", "min-consensus").getDouble(0.6d));
            isVPN = api.consensus(ip) >= consensus;
        } else {
            isVPN = api.cascade(ip);
        }

        if (isVPN) {
            if (cachedConfig.getDebug()) {
                proxy.getConsoleCommandSource().sendMessage(LogUtil.getHeading().append(TextComponent.of(event.getPlayer().getUsername()).color(TextColor.WHITE)).append(TextComponent.of(" found using a VPN. Kicking with defined message.").color(TextColor.DARK_RED)).build());
            }

            event.getPlayer().disconnect(TextComponent.of(config.getNode("kick", "message").getString("")));
        } else {
            proxy.getConsoleCommandSource().sendMessage(LogUtil.getHeading().append(TextComponent.of(event.getPlayer().getUsername()).color(TextColor.WHITE)).append(TextComponent.of(" passed VPN check.").color(TextColor.GREEN)).build());
        }
    }

    private String getIp(InetSocketAddress address) {
        if (address == null) {
            return null;
        }

        InetAddress host = address.getAddress();
        if (host == null) {
            return null;
        }

        return host.getHostAddress();
    }

    private double clamp(double min, double max, double val) { return Math.min(max, Math.max(min, val)); }
}
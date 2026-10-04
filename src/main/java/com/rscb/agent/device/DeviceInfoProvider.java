package com.rscb.agent.device;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/** Informasi perangkat lokal: hostname, daftar IP, dan OS. */
public class DeviceInfoProvider {

    private static final System.Logger LOG = System.getLogger(DeviceInfoProvider.class.getName());

    public String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Gagal membaca hostname", e);
            return "unknown-host";
        }
    }

    /** Alamat IPv4 dari interface aktif non-loopback. */
    public List<String> ipAddresses() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(ni.getInetAddresses())) {
                    String host = address.getHostAddress();
                    if (host.contains(":")) {
                        continue; // lewati IPv6, backend menyimpan string sederhana
                    }
                    if (!ips.contains(host)) {
                        ips.add(host);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Gagal membuka daftar network interface", e);
        }
        return ips;
    }

    public String os() {
        return System.getProperty("os.name") + " " + System.getProperty("os.arch");
    }
}

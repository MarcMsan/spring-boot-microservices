package com.vidopos.inventory;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.net.ServerSocket;

final class EmbeddedPostgresSupport {

    private EmbeddedPostgresSupport() {
    }

    static EmbeddedPostgres start() {
        try {
            int port;
            try (ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(true);
                port = socket.getLocalPort();
            }
            EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                    .setPort(port)
                    .setLocaleConfig("locale", "C")
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                }
            }));
            return postgres;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}

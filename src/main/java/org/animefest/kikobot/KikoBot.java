package org.animefest.kikobot;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;

public class KikoBot {
    private static final String PROPERTY_TOKEN = "token";

    public static JDABuilder builder;

    public static void main(String[] args) throws Exception {
        Properties properties = loadSettings();
        String token = properties.getProperty(PROPERTY_TOKEN);
        builder = new JDABuilder(AccountType.BOT);
        builder.addEventListener(new JDAEventListener());
        builder.setToken(token);
        builder.build();
    }

    private static Properties loadSettings() throws IOException {
        final String userHome = System.getProperty("user.home");
        final File propertiesFile = new File(userHome, ".kikobot.properties");
        Properties properties = new Properties();
        properties.load(new FileInputStream(propertiesFile));
        return properties;
    }

}
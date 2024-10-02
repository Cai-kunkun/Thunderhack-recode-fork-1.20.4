package thunder.hack.core.impl;

import thunder.hack.core.IManager;

import java.io.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class MacroManager implements IManager {
    private static CopyOnWriteArrayList<Macro> macros = new CopyOnWriteArrayList<>();

    public static void addMacro(Macro macro) {
        if (!macros.contains(macro)) {
            macros.add(macro);
        }
    }

    public void onLoad() {
        macros = new CopyOnWriteArrayList<>();
        try {
            File file = new File(ConfigManager.CONFIG_FOLDER_NAME + "/misc/macro.txt");

            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    while (reader.ready()) {
                        String[] nameKey = reader.readLine().split(":");
                        String name = nameKey[0];
                        String key = nameKey[1];
                        String command = nameKey[2];
                        addMacro(new Macro(name, command, Integer.parseInt(key)));
                    }

                }
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void saveMacro() {
        File file = new File(ConfigManager.CONFIG_FOLDER_NAME + "/misc/macro.txt");
        try {
            if (new File(ConfigManager.CONFIG_FOLDER_NAME).mkdirs()) {
                file.createNewFile();
            }
        } catch (Exception ignored) {

        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (Macro macro : macros) {
                writer.write(macro.name + ":" + macro.bind + ":" + macro.text + "\n");
            }
        } catch (Exception ignored) {
        }
    }

    public void removeMacro(Macro macro) {
        macros.remove(macro);
    }

    public CopyOnWriteArrayList<Macro> getMacros() {
        return macros;
    }

    public Macro getMacroByName(String n) {
        for (Macro m : getMacros())
            if (m.name.equalsIgnoreCase(n))
                return m;
        return null;
    }

    public record Macro(String name, String text, int bind) {
        public void runMacro() {
            if (mc.player == null) return;
            if (text.contains("/")) mc.player.networkHandler.sendChatCommand(text.replace("/", ""));
            else mc.player.networkHandler.sendChatMessage(text);
        }
    }
}

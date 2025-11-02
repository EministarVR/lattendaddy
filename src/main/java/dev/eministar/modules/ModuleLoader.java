package dev.eministar.modules;

import dev.eministar.command.Command;
import org.reflections.Reflections;

import java.util.Set;

public class ModuleLoader {
    public static Set<Class<? extends Command>> loadCommands() {
        Reflections reflections = new Reflections("dev.eministar.modules");
        return reflections.getSubTypesOf(Command.class);
    }
}

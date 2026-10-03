package dev.visualclient.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Шина событий: хуки в игре публикуют события через {@link #post}, а модули получают их
 * в методах, помеченных {@link Subscribe}, пока зарегистрированы через {@link #subscribe}.
 */
public final class EventBus {
    private record Listener(Object owner, MethodHandle handle, int priority) {
    }

    private final Map<Class<?>, List<Listener>> listeners = new ConcurrentHashMap<>();

    public void subscribe(Object owner) {
        for (Method method : collectMethods(owner.getClass())) {
            Subscribe annotation = method.getAnnotation(Subscribe.class);
            if (annotation == null) continue;
            if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new IllegalArgumentException("@Subscribe-метод должен принимать одно событие: " + method);
            }
            try {
                method.setAccessible(true);
                MethodHandle handle = MethodHandles.lookup().unreflect(method).bindTo(owner);
                List<Listener> list = listeners.computeIfAbsent(method.getParameterTypes()[0], k -> new CopyOnWriteArrayList<>());
                list.add(new Listener(owner, handle, annotation.priority()));
                list.sort(Comparator.comparingInt(Listener::priority).reversed());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Нет доступа к обработчику " + method, e);
            }
        }
    }

    public void unsubscribe(Object owner) {
        for (List<Listener> list : listeners.values()) {
            list.removeIf(listener -> listener.owner() == owner);
        }
    }

    public <T extends Event> T post(T event) {
        List<Listener> list = listeners.get(event.getClass());
        if (list == null) return event;
        for (Listener listener : list) {
            try {
                listener.handle().invoke(event);
            } catch (Throwable t) {
                // Ошибка в одном модуле не должна ронять игру.
                System.err.println("[VisualClient] Ошибка в обработчике " + listener.owner().getClass().getSimpleName());
                t.printStackTrace();
            }
        }
        return event;
    }

    private static List<Method> collectMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            methods.addAll(List.of(c.getDeclaredMethods()));
        }
        return methods;
    }
}

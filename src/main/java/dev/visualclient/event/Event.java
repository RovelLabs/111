package dev.visualclient.event;

/** Базовый класс всех событий клиента. */
public abstract class Event {
    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    /** Отмена имеет смысл только для событий, которые хук проверяет после публикации. */
    public void cancel() {
        this.cancelled = true;
    }
}

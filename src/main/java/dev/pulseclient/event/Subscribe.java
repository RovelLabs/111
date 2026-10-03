package dev.pulseclient.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Помечает метод-обработчик события. Метод должен принимать ровно один параметр — тип события.
 * Чем больше priority, тем раньше вызывается обработчик.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
    int priority() default 0;
}

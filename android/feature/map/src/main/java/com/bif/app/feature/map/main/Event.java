package com.bif.app.feature.map.main;

/**
 * Wraps a one-time event value so that LiveData observers don't re-process it after
 * re-subscription (e.g. on navigation back). The content is delivered exactly once;
 * subsequent calls to {@link #getContentIfNotHandled()} return null.
 */
public class Event<T> {
    private final T content;
    private boolean hasBeenHandled = false;

    public Event(T content) {
        this.content = content;
    }

    /**
     * Returns the content and marks this event as handled. Returns null if already handled.
     */
    public T getContentIfNotHandled() {
        if (hasBeenHandled) return null;
        hasBeenHandled = true;
        return content;
    }

    /** Allows peeking at the content without marking it as handled. */
    public T peekContent() {
        return content;
    }
}


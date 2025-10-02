package cz.mamstylcendy.cards.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class AbstractListenerTarget<L> {

    private final List<ListenerInfo> listeners = new ArrayList<>();

    public boolean addListener(L listener, Executor executor) {
        synchronized (listeners) {
            Objects.requireNonNull(listener);
            ListenerInfo info = new ListenerInfo(listener, executor);
            if (!listeners.contains(info)) {
                listeners.add(info);
                return true;
            }
            return false;
        }
    }

    public void removeListener(L listener) {
        synchronized (listeners) {
            Objects.requireNonNull(listener);
            ListenerInfo info = new ListenerInfo(listener, null);
            listeners.remove(info);
        }
    }

    protected int getListenerCount() {
        synchronized (listeners) {
            return listeners.size();
        }
    }

    protected boolean hasAnyListener() {
        synchronized (listeners) {
            return !listeners.isEmpty();
        }
    }

    protected boolean canInvokeListener(L listener) {
        return true;
    }

    protected void invokeListeners(Consumer<L> action) {
        synchronized (listeners) {
            for (ListenerInfo info : listeners) {
                if (!canInvokeListener(info.listener)) {
                    continue;
                }
                if (info.executor != null) {
                    info.executor.execute(() -> action.accept(info.listener));
                } else {
                    action.accept(info.listener);
                }
            }
        }
    }

    private class ListenerInfo {

        final L listener;
        final Executor executor;

        ListenerInfo(L listener, Executor executor) {
            this.listener = listener;
            this.executor = executor;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof AbstractListenerTarget.ListenerInfo that) {
                return Objects.equals(listener, that.listener);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(listener);
        }
    }
}

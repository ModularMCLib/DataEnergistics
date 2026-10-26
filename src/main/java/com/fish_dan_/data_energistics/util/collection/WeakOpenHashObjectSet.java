package com.fish_dan_.data_energistics.util.collection;

import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.NoSuchElementException;

/**
 * An equals-based weak set backed by fastutil's open-addressed hash set. Owners retain
 * their elements while they are needed. Access is confined to one thread; collected entries are
 * removed on subsequent operations. Iterators snapshot weak entries and retain only their next
 * element while it is ready to return. Elements must keep stable equals and hashCode while stored.
 */
public final class WeakOpenHashObjectSet<E> extends AbstractObjectSet<E> {

    private final ReferenceQueue<E> collected = new ReferenceQueue<>();
    private final ObjectOpenHashSet<Entry<E>> entries = new ObjectOpenHashSet<>();

    @Override
    public boolean add(E element) {
        expungeCollected();
        boolean added = entries.add(new Entry<>(element, collected));
        Reference.reachabilityFence(element);
        return added;
    }

    @Override
    public boolean contains(Object element) {
        expungeCollected();
        boolean present = entries.contains(new Entry<>(element, null));
        Reference.reachabilityFence(element);
        return present;
    }

    @Override
    public boolean remove(Object element) {
        expungeCollected();
        boolean removed = entries.remove(new Entry<>(element, null));
        Reference.reachabilityFence(element);
        return removed;
    }

    @Override
    public int size() {
        expungeCollected();
        // Clearing a weak reference can precede its delivery to the reference queue.
        entries.removeIf(entry -> entry.refersTo(null));
        return entries.size();
    }

    @Override
    public void clear() {
        entries.clear();
        expungeCollected();
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (E element : this) hash += element.hashCode();
        return hash;
    }

    @Override
    public ObjectIterator<E> iterator() {
        expungeCollected();
        ObjectIterator<Entry<E>> iterator = new ObjectArrayList<>(entries).iterator();
        return new ObjectIterator<>() {

            private E next;
            private Entry<E> nextEntry;
            private Entry<E> lastReturned;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                while (iterator.hasNext()) {
                    Entry<E> entry = iterator.next();
                    E element = entry.get();
                    if (element == null) {
                        entries.remove(entry);
                        continue;
                    }
                    next = element;
                    nextEntry = entry;
                    return true;
                }
                return false;
            }

            @Override
            public E next() {
                if (!hasNext()) throw new NoSuchElementException();
                E element = next;
                next = null;
                lastReturned = nextEntry;
                nextEntry = null;
                return element;
            }

            @Override
            public void remove() {
                Entry<E> entry = lastReturned;
                if (entry == null) throw new IllegalStateException();
                entries.remove(entry);
                lastReturned = null;
            }
        };
    }

    private void expungeCollected() {
        Reference<? extends E> reference;
        while ((reference = collected.poll()) != null) entries.remove(reference);
    }

    private static final class Entry<E> extends WeakReference<E> {

        private final int hash;

        private Entry(E element, ReferenceQueue<E> queue) {
            super(element, queue);
            this.hash = element.hashCode();
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Entry<?> other)) return false;
            E element = get();
            Object otherElement = other.get();
            return element != null && element.equals(otherElement);
        }
    }
}

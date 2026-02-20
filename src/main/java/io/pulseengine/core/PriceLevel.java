package io.pulseengine.core;

final class PriceLevel {
    final long priceTicks;
    BookOrder head;
    BookOrder tail;

    PriceLevel(long priceTicks) {
        this.priceTicks = priceTicks;
    }

    boolean isEmpty() {
        return head == null;
    }

    void addLast(BookOrder order) {
        order.prev = tail;
        order.next = null;
        order.level = this;
        if (tail == null) {
            head = order;
            tail = order;
            return;
        }
        tail.next = order;
        tail = order;
    }

    void remove(BookOrder order) {
        BookOrder p = order.prev;
        BookOrder n = order.next;
        if (p == null) {
            head = n;
        } else {
            p.next = n;
        }
        if (n == null) {
            tail = p;
        } else {
            n.prev = p;
        }
        order.prev = null;
        order.next = null;
        order.level = null;
    }

    void moveToTail(BookOrder order) {
        if (tail == order) {
            return;
        }
        remove(order);
        addLast(order);
    }

    long totalVisibleQty() {
        long total = 0;
        BookOrder p = head;
        while (p != null) {
            total += p.visibleQty;
            p = p.next;
        }
        return total;
    }
}

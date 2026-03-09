package io.pulseengine.core;

final class PriceLevel {
    long priceTicks;
    BookOrder head;
    BookOrder tail;
    long totalVisibleQty;

    void init(long priceTicks) {
        this.priceTicks = priceTicks;
        this.head = null;
        this.tail = null;
        this.totalVisibleQty = 0;
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
            totalVisibleQty += order.visibleQty;
            return;
        }
        tail.next = order;
        tail = order;
        totalVisibleQty += order.visibleQty;
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
        totalVisibleQty -= order.visibleQty;
    }

    void moveToTail(BookOrder order) {
        if (tail == order) {
            return;
        }
        remove(order);
        addLast(order);
    }

    long totalVisibleQty() {
        return totalVisibleQty;
    }
}
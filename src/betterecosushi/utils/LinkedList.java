package betterecosushi.utils;

public class LinkedList<T> {
    public int size = 0;
    public Node<T> head;
    public Node<T> end;
    public LinkedList() {

    }
    public void add(T obj) {
        if (end != null) {
            Node<T> newNode = new Node<T>(obj);
            newNode.prev = end;
            end.next = newNode;
            end = newNode;
        }
        else {
            head = new Node<T>(obj);
            end = head;
        }
        size++;
    }
    public Node<T> dequeue() {
        if (this.size > 0) {
            Node<T> removed = head;
            remove(head);
            return removed;
        }
        return null;
    }
    /**
     * O(n)
     */
    public boolean contains(T obj) {
        Node<T> node = head;
        while (node != null) {
            if (node.val.equals(obj)) {
                return true;
            }
            node = node.next;
        }
        return false;
    }
    public void remove(Node<T> node) {
        if (node.prev != null) {
            node.prev.next = node.next;
        }
        else {
            // deal with head
            head = node.next;
        }
        if (node.next != null) {
            node.next.prev = node.prev;
        }
        else {
            end = node.prev;
        }
        node = null;
        size--;
    }
    public boolean remove(T obj) {
        Node<T> node = head;
        while (node != null) {
            if (node.val.equals(obj)) {
                remove(node);
                return true;
            }
            node = node.next;
        }
        return false;
    }

}
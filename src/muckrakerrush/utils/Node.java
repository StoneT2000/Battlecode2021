package muckrakerrush.utils;

public class Node<T> {
    public Node<T> next;
    public Node<T> prev;
    public T val;
    public Node(T obj) {
        val = obj;
    }
}
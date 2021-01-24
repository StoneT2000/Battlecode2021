package betterecosushi.utils;
// using this as node values, we can compare keys and store values still as linkedlist contain method uses .equals
public class HashMapNodeVal<K,V> {
    public K key;
    public V val;
    HashMapNodeVal(K key, V val) {
        this.key = key;
        this.val = val;
    }
    @Override
    public boolean equals(Object o) {
        return this.key.equals(((HashMapNodeVal<K, V>) o).key);
    }
    
    @Override
    public String toString() {
        return "K: " + key.toString() + " - V: " + val.toString();
    }
}
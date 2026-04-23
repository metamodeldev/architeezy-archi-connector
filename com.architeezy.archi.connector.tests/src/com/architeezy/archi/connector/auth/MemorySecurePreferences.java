/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.StorageException;

/**
 * In-memory {@link ISecurePreferences} for unit tests. Implements the subset of
 * the API that {@link TokenStore} actually exercises: {@link #node(String)},
 * {@link #get(String, String)}, {@link #put(String, String, boolean)},
 * {@link #remove(String)}, {@link #flush()}. Other methods throw
 * {@link UnsupportedOperationException} so that accidental reliance on them
 * fails loudly.
 */
public final class MemorySecurePreferences implements ISecurePreferences {

    private final MemorySecurePreferences root;

    private final String path;

    private final Map<String, Map<String, String>> allValues;

    /** Creates an empty root preferences node. */
    public MemorySecurePreferences() {
        this.root = this;
        this.path = "/";
        this.allValues = new ConcurrentHashMap<>();
    }

    private MemorySecurePreferences(MemorySecurePreferences root, String path) {
        this.root = root;
        this.path = path;
        this.allValues = root.allValues;
    }

    private Map<String, String> values() {
        return allValues.computeIfAbsent(path, k -> new ConcurrentHashMap<>());
    }

    @Override
    public ISecurePreferences node(String nodePath) {
        var absolute = nodePath.startsWith("/")
                ? nodePath
                : ("/".equals(path) ? "/" + nodePath : path + "/" + nodePath);
        return new MemorySecurePreferences(root, absolute);
    }

    @Override
    public String get(String key, String def) throws StorageException {
        var m = allValues.get(path);
        if (m == null) {
            return def;
        }
        return m.getOrDefault(key, def);
    }

    @Override
    public void put(String key, String value, boolean encrypt) throws StorageException {
        values().put(key, value);
    }

    @Override
    public void remove(String key) {
        var m = allValues.get(path);
        if (m != null) {
            m.remove(key);
        }
    }

    @Override
    public void flush() {
        // no-op: in-memory backing needs no flushing
    }

    @Override
    public String name() {
        var slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    @Override
    public String absolutePath() {
        return path;
    }

    @Override
    public ISecurePreferences parent() {
        if ("/".equals(path)) {
            return null;
        }
        var slash = path.lastIndexOf('/');
        return new MemorySecurePreferences(root, slash == 0 ? "/" : path.substring(0, slash));
    }

    @Override
    public boolean nodeExists(String nodePath) {
        var absolute = nodePath.startsWith("/")
                ? nodePath
                : ("/".equals(path) ? "/" + nodePath : path + "/" + nodePath);
        return allValues.containsKey(absolute);
    }

    @Override
    public void removeNode() {
        allValues.remove(path);
    }

    @Override
    public void clear() {
        var m = allValues.get(path);
        if (m != null) {
            m.clear();
        }
    }

    @Override
    public String[] keys() {
        var m = allValues.get(path);
        return m == null ? new String[0] : m.keySet().toArray(new String[0]);
    }

    @Override
    public String[] childrenNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEncrypted(String key) {
        return false;
    }

    @Override
    public void putInt(String key, int value, boolean encrypt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getInt(String key, int def) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putLong(String key, long value, boolean encrypt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLong(String key, long def) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putBoolean(String key, boolean value, boolean encrypt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putFloat(String key, float value, boolean encrypt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float getFloat(String key, float def) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putDouble(String key, double value, boolean encrypt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getDouble(String key, double def) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putByteArray(String key, byte[] value, boolean encrypt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getByteArray(String key, byte[] def) {
        throw new UnsupportedOperationException();
    }

}

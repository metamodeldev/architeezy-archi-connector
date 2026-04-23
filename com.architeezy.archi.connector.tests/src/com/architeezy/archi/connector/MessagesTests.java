/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

class MessagesTests {

    @Test
    void allPublicStringFieldsAreBound() throws IllegalAccessException {
        var fields = Messages.class.getDeclaredFields();
        var stringFieldCount = 0;
        for (Field f : fields) {
            int mods = f.getModifiers();
            if (Modifier.isPublic(mods)
                    && Modifier.isStatic(mods)
                    && !Modifier.isFinal(mods)
                    && f.getType() == String.class) {
                stringFieldCount++;
                var value = (String) f.get(null);
                assertNotNull(value, "Message not bound: " + f.getName());
                assertFalse(value.startsWith("NLS missing message"),
                        "Missing message binding for: " + f.getName());
            }
        }
        assertTrue(stringFieldCount > 0, "Messages class has no NLS fields");
    }

}

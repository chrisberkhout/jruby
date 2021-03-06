/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.stdlib;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import org.jruby.truffle.RubyContext;
import org.jruby.truffle.core.CoreClass;
import org.jruby.truffle.core.CoreMethod;
import org.jruby.truffle.core.CoreMethodNode;

@CoreClass(name = "Truffle::Etc")
public abstract class EtcNodes {

    @CoreMethod(names = "nprocessors", needsSelf = false)
    public abstract static class NProcessors extends CoreMethodNode {

        @TruffleBoundary
        @Specialization
        public int nprocessors() {
            return Runtime.getRuntime().availableProcessors();
        }

    }

}

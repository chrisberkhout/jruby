/*
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.translator;

import com.oracle.truffle.api.Source;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameSlot;
import org.jruby.ast.BlockArgNode;
import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.nodes.control.SequenceNode;
import org.jruby.truffle.nodes.core.ArrayIndexNodeFactory;
import org.jruby.truffle.nodes.methods.arguments.*;
import org.jruby.truffle.nodes.methods.locals.ReadLocalVariableNodeFactory;
import org.jruby.truffle.nodes.methods.locals.WriteLocalVariableNodeFactory;
import org.jruby.truffle.runtime.NilPlaceholder;
import org.jruby.truffle.runtime.RubyContext;

import java.util.ArrayList;
import java.util.List;

public class LoadArgumentsTranslator extends Translator {


    private final boolean isBlock;
    private final BodyTranslator methodBodyTranslator;
    private final List<FrameSlot> arraySlotStack = new ArrayList<>();

    private enum State {
        PRE,
        POST
    }

    private State state;

    private org.jruby.ast.ArgsNode argsNode;

    public LoadArgumentsTranslator(RubyContext context, Source source, boolean isBlock, BodyTranslator methodBodyTranslator) {
        super(context, source);
        this.isBlock = isBlock;
        this.methodBodyTranslator = methodBodyTranslator;
    }

    @Override
    public RubyNode visitArgsNode(org.jruby.ast.ArgsNode node) {
        argsNode = node;

        final SourceSection sourceSection = translate(node.getPosition());

        final List<RubyNode> sequence = new ArrayList<>();

        if (node.getPre() != null) {
            state = State.PRE;
            for (org.jruby.ast.Node arg : node.getPre().childNodes()) {
                sequence.add(arg.accept(this));
            }
        }

        if (node.getOptArgs() != null) {
            // (BlockNode 0, (OptArgNode:a 0, (LocalAsgnNode:a 0, (FixnumNode 0))), ...)
            for (org.jruby.ast.Node arg : node.getOptArgs().childNodes()) {
                sequence.add(arg.accept(this));
            }
        }

        if (node.getPost() != null) {
            state = State.POST;
            for (org.jruby.ast.Node arg : node.getPost().childNodes()) {
                sequence.add(arg.accept(this));
            }
        }

        if (node.getRestArgNode() != null) {
            methodBodyTranslator.getEnvironment().hasRestParameter = true;
            sequence.add(node.getRestArgNode().accept(this));
        }

        if (node.getBlock() != null) {
            sequence.add(node.getBlock().accept(this));
        }

        return SequenceNode.sequence(context, sourceSection, sequence);
    }

    @Override
    public RubyNode visitArgumentNode(org.jruby.ast.ArgumentNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final RubyNode readNode;

        if (useArray()) {
            readNode = ArrayIndexNodeFactory.create(context, sourceSection, node.getIndex(), loadArray(sourceSection));
        } else {
            if (state == State.PRE) {
                readNode = new ReadPreArgumentNode(context, sourceSection, node.getIndex(), isBlock ? MissingArgumentBehaviour.NIL : MissingArgumentBehaviour.RUNTIME_ERROR);
            } else if (state == State.POST) {
                readNode = new ReadPostArgumentNode(context, sourceSection, (argsNode.getPreCount() + argsNode.getOptionalArgsCount() + argsNode.getPostCount()) - node.getIndex() - 1);
            } else {
                throw new IllegalStateException();
            }
        }

        final FrameSlot slot = methodBodyTranslator.getEnvironment().getFrameDescriptor().findFrameSlot(node.getName());
        return WriteLocalVariableNodeFactory.create(context, sourceSection, slot, readNode);
    }

    @Override
    public RubyNode visitRestArgNode(org.jruby.ast.RestArgNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final RubyNode readNode = new ReadRestArgumentNode(context, sourceSection, node.getIndex());
        final FrameSlot slot = methodBodyTranslator.getEnvironment().getFrameDescriptor().findFrameSlot(node.getName());
        return WriteLocalVariableNodeFactory.create(context, sourceSection, slot, readNode);
    }

    @Override
    public RubyNode visitBlockArgNode(org.jruby.ast.BlockArgNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final RubyNode readNode = new ReadBlockNode(context, sourceSection, NilPlaceholder.INSTANCE);
        final FrameSlot slot = methodBodyTranslator.getEnvironment().getFrameDescriptor().findFrameSlot(node.getName());
        return WriteLocalVariableNodeFactory.create(context, sourceSection, slot, readNode);
    }

    @Override
    public RubyNode visitOptArgNode(org.jruby.ast.OptArgNode node) {
        // (OptArgNode:a 0, (LocalAsgnNode:a 0, (FixnumNode 0)))

        return node.childNodes().get(0).accept(this);
    }

    @Override
    public RubyNode visitLocalAsgnNode(org.jruby.ast.LocalAsgnNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final RubyNode defaultValue = node.getValueNode().accept(this);
        final RubyNode readNode = new ReadOptionalArgumentNode(context, sourceSection, node.getIndex(), node.getIndex() + argsNode.getPostCount() + 1, defaultValue);
        final FrameSlot slot = methodBodyTranslator.getEnvironment().getFrameDescriptor().findFrameSlot(node.getName());
        return WriteLocalVariableNodeFactory.create(context, sourceSection, slot, readNode);
    }

    @Override
    protected RubyNode defaultVisit(org.jruby.ast.Node node) {
        // For normal expressions in the default value for optional arguments, use the normal body translator

        return node.accept(methodBodyTranslator);
    }

    public void pushArraySlot(FrameSlot slot) {
        arraySlotStack.add(slot);
    }

    protected boolean useArray() {
        return !arraySlotStack.isEmpty();
    }

    protected RubyNode loadArray(SourceSection sourceSection) {
        return ReadLocalVariableNodeFactory.create(context, sourceSection, arraySlotStack.get(0));
    }

}

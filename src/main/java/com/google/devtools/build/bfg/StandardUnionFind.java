// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.bfg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterators.filter;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A Union-Find implementation.
 *
 * <p>This class implements Union-Find algorithm with rank and path compression.
 *
 * <p>See <a href="http://www.algorithmist.com/index.php?title=Union_Find&oldid=7575">
 * algorithmist</a> for more detail.
 *
 * @param <E> element type
 * @author Zilin Du
 * @author Jesse Wilson
 * @author Andrew Moedinger
 * @author Grzegorz Glowaty
 */
class StandardUnionFind<E> {

  /** All values with the same root node are in the same equivalence set. */
  private final Map<E, Node<E>> elmap = Maps.newLinkedHashMap();

  /** Creates an empty UnionFind structure. */
  StandardUnionFind() {}

  public void add(@Nullable E e) {
    union(e, e);
  }

  E union(@Nullable E a, @Nullable E b) {
    Node<E> nodeA = findRootOrCreateNode(a);
    Node<E> nodeB = findRootOrCreateNode(b);

    if (nodeA == nodeB) {
      return nodeA.element;
    }
    // If possible, prefer nodeA over nodeB, to preserve insertion order.
    if (nodeA.rank >= nodeB.rank) {
      nodeB.parent = nodeA;
      nodeA.size += nodeB.size;
      if (nodeA.rank == nodeB.rank) {
        nodeA.rank++;
      }
      return nodeA.element;
    }
    nodeA.parent = nodeB;
    nodeB.size += nodeA.size;
    E temp = nodeB.element;
    nodeB.element = nodeA.element;
    nodeA.element = temp;
    return nodeB.element;
  }

  private E find(@Nullable E e) {
    checkArgument(elmap.containsKey(e), "Element does not exist: %s", e);
    return findRoot(elmap.get(e)).element;
  }

  public boolean areEquivalent(@Nullable E a, @Nullable E b) {
    E aRep = find(a);
    E bRep = find(b);
    return aRep == bRep;
  }

  public Set<E> elements() {
    return Collections.unmodifiableSet(elmap.keySet());
  }

  Collection<Set<E>> allEquivalenceClasses() {
    Map<Node<E>, ImmutableSet.Builder<E>> groupsTmp = Maps.newLinkedHashMap();
    for (Node<E> elem : elmap.values()) {
      Node<E> root = findRoot(elem);
      ImmutableSet.Builder<E> builder = groupsTmp.get(root);
      if (builder == null) {
        builder = ImmutableSet.builder();
        groupsTmp.put(root, builder);
      }
      builder.add(elem.element);
    }
    ImmutableList.Builder<Set<E>> result = ImmutableList.builder();
    for (ImmutableSet.Builder<E> group : groupsTmp.values()) {
      result.add(group.build());
    }
    return result.build();
  }

  /**
   * If e is already in a non-trivial equivalence class, that is, a class with more than two
   * elements, then return the {@link Node} corresponding to the representative element. Otherwise,
   * if e sits in an equivalence class by itself, then create a {@link Node}, put it into elmap and
   * return it.
   */
  private Node<E> findRootOrCreateNode(E e) {
    Node<E> node = elmap.get(e);
    if (node != null) {
      return findRoot(node);
    }
    node = new Node<E>(e);
    elmap.put(e, node);
    return node;
  }

  /**
   * Given a {@link Node}, walk the parent field as far as possible, until reaching the root, which
   * is the {@link Node} for the current representative of this equivalence class. To achieve low
   * runtime complexity, also compress the path, by making each node a direct child of the root.
   */
  private Node<E> findRoot(Node<E> node) {
    if (node.parent != node) {
      node.parent = findRoot(node.parent);
    }
    return node.parent;
  }

  public Set<E> findAll(@Nullable final E value) {
    checkArgument(elmap.containsKey(value), "Element does not exist: %s", value);

    final Predicate<Object> isSameRoot =
        new Predicate<Object>() {

          /** some node that's close to the root, or null */
          Node<E> nodeForValue = elmap.get(value);

          @Override
          public boolean apply(@Nullable Object b) {
            if (Objects.equal(value, b)) {
              return true;
            }
            Node<E> nodeForB = elmap.get(b);
            if (nodeForB == null) {
              return false;
            }
            nodeForValue = findRoot(nodeForValue);
            return findRoot(nodeForB) == nodeForValue;
          }
        };

    return new AbstractSet<E>() {

      @Override
      public boolean contains(Object o) {
        return isSameRoot.apply(o);
      }

      @Override
      public Iterator<E> iterator() {
        return filter(elmap.keySet().iterator(), isSameRoot);
      }

      @Override
      public int size() {
        return findRoot(elmap.get(value)).size;
      }
    };
  }

  /** The internal node representation. */
  private static class Node<E> {
    /** The parent node of this element. */
    Node<E> parent;

    /** The element represented by this node. */
    E element;

    /** A bound on the depth of the subtree rooted to this node. */
    int rank = 0;

    /**
     * If this node is the root of a tree, this is the number of elements in the tree. Otherwise,
     * it's undefined.
     */
    int size = 1;

    Node(E element) {
      this.parent = this;
      this.element = element;
    }
  }
}

package sootup.callgraph;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 2019-2020 Christian Brüggemann, Markus Schmidt
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import com.google.common.base.Preconditions;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.jgrapht.graph.DefaultDirectedGraph;
import sootup.core.signatures.MethodSignature;
import sootup.core.signatures.SootClassMemberSignature;
import sootup.java.core.types.JavaClassType;

/** This class implements a mutable call graph as a graph. */
public final class GraphBasedCallGraph implements MutableCallGraph {

  /**
   * This internal class is used to describe a vertex in the graph. The vertex is defined by a
   * method signature that describes the method.
   */
  private static class Vertex {
    @Nonnull final MethodSignature methodSignature;

    private Vertex(@Nonnull MethodSignature methodSignature) {
      this.methodSignature = methodSignature;
    }
  }

  /** This internal class is used to describe the edge in the graph. */
  private static class Edge {}

  @Nonnull private final DefaultDirectedGraph<Vertex, Edge> graph;
  @Nonnull private final Map<MethodSignature, Vertex> signatureToVertex;
  // TODO: [ms] typeToVertices is not used in a useful way, yet?
  @Nonnull private final Map<JavaClassType, Set<Vertex>> typeToVertices;

  /** The constructor of the graph based call graph. it initializes the call graph object. */
  GraphBasedCallGraph() {
    graph = new DefaultDirectedGraph<>(null, null, false);
    signatureToVertex = new HashMap<>();
    typeToVertices = new HashMap<>();
  }

  private GraphBasedCallGraph(
      @Nonnull DefaultDirectedGraph<Vertex, Edge> graph,
      @Nonnull Map<MethodSignature, Vertex> signatureToVertex,
      @Nonnull Map<JavaClassType, Set<Vertex>> typeToVertices) {
    this.graph = graph;
    this.signatureToVertex = signatureToVertex;
    this.typeToVertices = typeToVertices;
  }

  @Override
  public void addMethod(@Nonnull MethodSignature calledMethod) {
    Vertex v = new Vertex(calledMethod);
    graph.addVertex(v);
    signatureToVertex.put(calledMethod, v);
  }

  @Override
  public void addCall(
      @Nonnull MethodSignature sourceMethod, @Nonnull MethodSignature targetMethod) {
    graph.addEdge(vertexOf(sourceMethod), vertexOf(targetMethod), new Edge());
  }

  @Nonnull
  @Override
  public Set<MethodSignature> getMethodSignatures() {
    return signatureToVertex.keySet();
  }

  @Nonnull
  @Override
  public Set<MethodSignature> callsFrom(@Nonnull MethodSignature sourceMethod) {
    return graph.outgoingEdgesOf(vertexOf(sourceMethod)).stream()
        .map(graph::getEdgeTarget)
        .map(targetVertex -> targetVertex.methodSignature)
        .collect(Collectors.toSet());
  }

  @Nonnull
  @Override
  public Set<MethodSignature> callsTo(@Nonnull MethodSignature targetMethod) {
    return graph.incomingEdgesOf(vertexOf(targetMethod)).stream()
        .map(graph::getEdgeSource)
        .map(targetVertex -> targetVertex.methodSignature)
        .collect(Collectors.toSet());
  }

  @Override
  public boolean containsMethod(@Nonnull MethodSignature method) {
    return signatureToVertex.containsKey(method);
  }

  @Override
  public boolean containsCall(
      @Nonnull MethodSignature sourceMethod, @Nonnull MethodSignature targetMethod) {
    if (!containsMethod(sourceMethod) || !containsMethod(targetMethod)) {
      return false;
    }
    return graph.containsEdge(vertexOf(sourceMethod), vertexOf(targetMethod));
  }

  @Override
  public int callCount() {
    return graph.edgeSet().size();
  }

  @Override
  public String exportAsDot() {
    StringBuilder dotFormatBuilder = new StringBuilder();
    // The edgeSet is first sorted with the sourceMethod first and then targetMethod. It is sorted
    // by className, then the method name
    // and then the parameters.
    graph.edgeSet().stream()
        .sorted(
            Comparator.comparing(
                    (Edge edge) -> {
                      Vertex edgeSource = graph.getEdgeSource(edge);
                      return edgeSource.methodSignature.getDeclClassType().getClassName();
                    })
                .thenComparing(
                    (Edge edge) -> {
                      Vertex edgeSource = graph.getEdgeSource(edge);
                      return edgeSource.methodSignature.getName();
                    })
                .thenComparing(
                    (Edge edge) -> {
                      Vertex edgeSource = graph.getEdgeSource(edge);
                      return edgeSource.methodSignature.getParameterTypes().toString();
                    })
                .thenComparing(
                    (Edge edge) -> {
                      Vertex edgeTarget = graph.getEdgeTarget(edge);
                      return edgeTarget.methodSignature.getDeclClassType().getClassName();
                    })
                .thenComparing(
                    (Edge edge) -> {
                      Vertex edgeTarget = graph.getEdgeTarget(edge);
                      return edgeTarget.methodSignature.getName();
                    })
                .thenComparing(
                    (Edge edge) -> {
                      Vertex edgeTarget = graph.getEdgeTarget(edge);
                      return edgeTarget.methodSignature.getParameterTypes().toString();
                    }))
        .forEach(
            edge -> {
              Vertex sourceVertex = graph.getEdgeSource(edge);
              Vertex targetVertex = graph.getEdgeTarget(edge);
              dotFormatBuilder
                  .append("\t")
                  .append("\"" + sourceVertex.methodSignature + "\"")
                  .append(" -> ")
                  .append("\"" + targetVertex.methodSignature + "\"")
                  .append(";\n");
            });

    return "strict digraph ObjectGraph {\n" + dotFormatBuilder + "}";
  }

  @SuppressWarnings("unchecked") // (graph.clone() preserves generic properties)
  @Nonnull
  @Override
  public MutableCallGraph copy() {
    return new GraphBasedCallGraph(
        (DefaultDirectedGraph<Vertex, Edge>) graph.clone(),
        new HashMap<>(signatureToVertex),
        new HashMap<>(typeToVertices));
  }

  /**
   * it returns the vertex of the graph that describes the given method signature in the call graph.
   *
   * @param method the method signature searched in the call graph
   * @return the vertex of the requested method signature.
   */
  @Nonnull
  private Vertex vertexOf(@Nonnull MethodSignature method) {
    Vertex methodVertex = signatureToVertex.get(method);
    Preconditions.checkNotNull(methodVertex, "Node for " + method + " has not been added yet");
    return methodVertex;
  }

  /**
   * This method exports the call graph in a human-readable string. The String lists all nodes in
   * the call graph. For each node it also lists the outgoing and incoming edges. An outgoing edge
   * is marked by a "To" and an incoming edge by a "From" The nodes, incoming edges, and outgoing
   * edges are sorted in order by the classname, method name, parameter list
   *
   * @return a string containing all nodes and edges of the call graph.
   */
  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder("GraphBasedCallGraph(" + callCount() + ")");
    Set<MethodSignature> signatures = signatureToVertex.keySet();
    if (signatures.isEmpty()) {
      stringBuilder.append(" is empty");
    } else {
      stringBuilder.append(":\n");
      signatures.stream()
          .sorted(
              Comparator.comparing((MethodSignature o) -> o.getDeclClassType().toString())
                  .thenComparing(SootClassMemberSignature::getName)
                  .thenComparing(o -> o.getParameterTypes().toString()))
          .forEach(
              method -> {
                stringBuilder.append(method).append(":\n");
                callsFrom(method).stream()
                    .sorted(
                        Comparator.comparing((MethodSignature o) -> o.getDeclClassType().toString())
                            .thenComparing(SootClassMemberSignature::getName)
                            .thenComparing(o -> o.getParameterTypes().toString()))
                    .forEach(m -> stringBuilder.append("\tto ").append(m).append("\n"));
                callsTo(method).stream()
                    .sorted(
                        Comparator.comparing((MethodSignature o) -> o.getDeclClassType().toString())
                            .thenComparing(SootClassMemberSignature::getName)
                            .thenComparing(o -> o.getParameterTypes().toString()))
                    .forEach(m -> stringBuilder.append("\tfrom ").append(m).append("\n"));
                stringBuilder.append("\n");
              });
    }
    return stringBuilder.toString();
  }
}

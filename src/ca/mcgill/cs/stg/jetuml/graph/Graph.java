/*******************************************************************************
 * JetUML - A desktop application for fast UML diagramming.
 *
 * Copyright (C) 2015 by the contributors of the JetUML project.
 *
 * See: https://github.com/prmr/JetUML
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/

package ca.mcgill.cs.stg.jetuml.graph;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.DefaultPersistenceDelegate;
import java.beans.Encoder;
import java.beans.Statement;
import java.util.ArrayList;
import java.util.Collection;

import ca.mcgill.cs.stg.jetuml.framework.GraphModificationListener;
import ca.mcgill.cs.stg.jetuml.framework.Grid;

/**
 *  A graph consisting of selectable nodes and edges.
 */
public abstract class Graph
{
	protected GraphModificationListener aModListener;
	protected ArrayList<Node> aRootNodes; // Only nodes without a parent are tracked by the graph.
	protected ArrayList<Edge> aEdges;
	protected transient ArrayList<Node> aNodesToBeRemoved;
	protected transient ArrayList<Edge> aEdgesToBeRemoved;
	private transient boolean aNeedsLayout;
	private transient Rectangle2D aMinBounds;

	/**
	 * Constructs a graph with no nodes or edges.
	 */
	public Graph()
	{
		aRootNodes = new ArrayList<>();
		aEdges = new ArrayList<>();
		aNodesToBeRemoved = new ArrayList<>();
		aEdgesToBeRemoved = new ArrayList<>();
		aModListener = new GraphModificationListener();
		aNeedsLayout = true;
	}
	
	/**
	 * True if it would make sense (according to graph-specific
	 * semantics) to change the parent of pNode if it is moved 
	 * to location pPoint. Returns false by default.
	 * @param pNode The node to test.
	 * @param pPoint The point where the node is moved.
	 * @return True if we should assign a new parent to the node.
	 */
	public boolean canChangeParentIfMovedTo(Node pNode, Point2D pPoint)
	{
		return false;
	}
	
	/**
	 * Updates pNode to have as parent whatever node is a pPoint.
	 * @param pNode The node to reassign
	 * @param pPoint The point where the parent should be.
	 */
	public void reassignParent(ChildNode pNode, Point2D pPoint)
	{}

	/**
	 * Adds the modification listener.
	 * @param pModListener the GraphModificationListener for this Graph.
	 */
	public void addModificationListener(GraphModificationListener pModListener)
	{
		aModListener = pModListener;
	}

	/**
	 * @return The file extension (including the dot) corresponding
	 * to files of this diagram type.
	 */
	public abstract String getFileExtension();

	/**
	 * @return A short description of this diagram, usually
	 * ending in "Diagram", e.g., "State Diagram".
	 */
	public abstract String getDescription();
	
	/**
	 * If certain types of diagrams require additional behavior
	 * following the addition of an edge to a graph, they can
	 * override this method to perform that behavior.
	 * @param pOrigin The origin node 
	 * @param pEdge The edge to add
	 * @param pPoint1 a point in the starting node
	 * @param pPoint2 a point in the end node.
	 */
	protected void addEdge(Node pOrigin, Edge pEdge, Point2D pPoint1, Point2D pPoint2)
	{}

	private PointNode createPointNodeIfAllowed(Node pNode1, Edge pEdge, Point2D pPoint2)
	{
		if(pNode1 instanceof NoteNode && pEdge instanceof NoteEdge)
		{
			PointNode lReturn = new PointNode();
			lReturn.translate(pPoint2.getX(), pPoint2.getY());
			return lReturn;
		}
		else
		{
			return null;
		}
	}
	
	/**
	 * Adds an edge to the graph that joins the nodes containing
	 * the given points. If the points aren't both inside nodes,
	 * then no edge is added.
	 * @param pEdge the edge to add
	 * @param pPoint1 a point in the starting node
	 * @param pPoint2 a point in the ending node
	 * @return true if the edge was connected
	 */
	public boolean connect(Edge pEdge, Point2D pPoint1, Point2D pPoint2)
	{
		Node node1 = findNode(pPoint1);
		if( node1 == null )
		{
			return false;
		}
		
		Node node2 = findNode(pPoint2);
		if( node1 instanceof NoteNode )
		{
			node2 = createPointNodeIfAllowed(node1, pEdge, pPoint2);
		}
		
		if(!canConnect(pEdge, node1, node2, pPoint2))
		{
			return false;
		}

		pEdge.connect(node1, node2);
			
		// In case the down-call to addEdge introduces additional 
		// operations that should be compounded with the edge addition
		aModListener.startCompoundListening();
		addEdge(node1, pEdge, pPoint1, pPoint2);
		aEdges.add(pEdge);
		aModListener.edgeAdded(this, pEdge);
		
		if(!aRootNodes.contains(pEdge.getEnd()) && pEdge.getEnd() instanceof PointNode )
		{
			aRootNodes.add(pEdge.getEnd());
		}
		aNeedsLayout = true;
		aModListener.endCompoundListening();
		return true;
	}

	/**
	 * Adds a node to the graph so that the top left corner of
	 * the bounding rectangle is at the given point.
	 * @param pNode the node to add
	 * @param pPoint the desired location
	 * @return True if the node was added.
	 */
	public boolean add(Node pNode, Point2D pPoint)
	{
		Rectangle2D bounds = pNode.getBounds();
		pNode.translate(pPoint.getX() - bounds.getX(), pPoint.getY() - bounds.getY()); 
		
		aModListener.nodeAdded(this, pNode);
		if( !(pNode instanceof ChildNode && ((ChildNode)pNode).getParent() != null) )
		{
			// Only add root nodes
			aRootNodes.add(pNode);
		}
		aNeedsLayout = true;
		return true;
	}

	/**
      * Finds a node containing the given point. Always returns
      * the deepest child and the last one in a list.
      * @param pPoint a point
      * @return a node containing pPoint or null if no nodes contain pPoint
      */
	public Node findNode(Point2D pPoint)
	{
		Node result = null;
		for( Node node : aRootNodes )
		{
			Node temp = deepFindNode(node, pPoint);
			if( temp != null )
			{
				result = temp;
			}
		}
		return result;
	}
	
	private Node deepFindNode( Node pNode, Point2D pPoint )
	{
		Node node = null;
		if( pNode instanceof ParentNode )
		{
			for( Node child : ((ParentNode) pNode).getChildren())
			{
				node = deepFindNode(child, pPoint);
				if( node != null )
				{
					return node;
				}
			}
		}
		if( pNode.contains(pPoint))
		{
			return pNode;
		}
		else
		{
			return null;
		}
	}

	/**
	 * Finds an edge containing the given point.
	 * @param pPoint a point
	 * @return an edge containing p or null if no edges contain p
	 */
	public Edge findEdge(Point2D pPoint)
	{
		for (int i = aEdges.size() - 1; i >= 0; i--)
		{
			Edge e = aEdges.get(i);
			if(e.contains(pPoint))
			{
				return e;
			}
		}
		return null;
	}

	/**
	 * Returns all edges connected to the given node.
	 * @param pNode The Node to query for Edges.
	 * @return an ArrayList of Edges from the Node pNode.
	 */
	public ArrayList<Edge> getNodeEdges(Node pNode)
	{
		ArrayList<Edge> toRet = new ArrayList<Edge>();
		for(int i = 0; i < aEdges.size(); i++)
		{
			Edge e = aEdges.get(i);
			if((e.getStart() == pNode || e.getEnd() == pNode) && !aEdgesToBeRemoved.contains(e))
			{
				toRet.add(e);
			}
		}
		return toRet;
	}
	
	/**
	 * Returns true iif there exists an edge of type pType between
	 * nodes pStart and pEnd. The direction matter, and the type
	 * testing is for the exact type pType, without using polymorphism.
	 * @param pType The type of edge to check for.
	 * @param pStart The start node.
	 * @param pEnd The end node.
	 * @return True if and only if there is an edge of type pType that
	 * starts at node pStart and ends at node pEnd.
	 */
	public boolean existsEdge(Class<?> pType, Node pStart, Node pEnd)
	{
		assert pType !=null && pStart != null && pEnd != null;
		for( Edge edge : getEdges() )
		{
			if( edge.getClass() == pType && edge.getStart() == pStart && edge.getEnd() == pEnd )
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Draws the graph.
	 * @param pGraphics2D the graphics context
	 * @param pGrid The grid
	 */
	public void draw(Graphics2D pGraphics2D, Grid pGrid)
	{
		layout(pGraphics2D, pGrid);

		for(int i = 0; i < aRootNodes.size(); i++)
		{
			Node n = aRootNodes.get(i);
			drawNode(n, pGraphics2D);
		}

		for(int i = 0; i < aEdges.size(); i++)
		{
			Edge e = aEdges.get(i);
			e.draw(pGraphics2D);
		}
	}
	
	private void drawNode(Node pNode, Graphics2D pGraphics2D)
	{
		pNode.draw(pGraphics2D);
		if( pNode instanceof ParentNode )
		{
			for( Node node : ((ParentNode) pNode).getChildren())
			{
				drawNode(node, pGraphics2D);
			}
		}
	}

	/**
	 * Removes a node and all edges that start or end with that node.
	 * @param pNode the node to remove
	 * TODO: prmr the child handling stuff can be removed? It's all handled 
	 * in the specific diagrams.
	 */
	public void removeNode(Node pNode)
	{
		if(aNodesToBeRemoved.contains(pNode))
		{
			return;
		}
		aModListener.startCompoundListening();
		aNodesToBeRemoved.add(pNode);
		
		if(pNode instanceof ParentNode)
		{
			ArrayList<ChildNode> children = new ArrayList<ChildNode>(((ParentNode) pNode).getChildren());
			//We create a shallow clone so deleting children does not affect the loop
			for(Node childNode: children)
			{
				removeNode(childNode);
			}
		}

		// Notify all nodes that pNode is being removed. TODO Navigate all nodes, not just root nodes
		for(Node node : aRootNodes)
		{
			removeFromParent( node, pNode );
		}
		
		// Notify all edges that pNode is being removed.
		for(Edge edge : aEdges)
		{
			if(edge.getStart() == pNode || edge.getEnd() == pNode)
			{
				removeEdge(edge);
			}
		}
		aModListener.nodeRemoved(this, pNode);
		aModListener.endCompoundListening();
		aNeedsLayout = true;
	}
	
	private static void removeFromParent(Node pParent, Node pToRemove)
	{
		if( pParent instanceof ParentNode )
		{
			if( pToRemove instanceof ChildNode && ((ChildNode) pToRemove).getParent() == pParent )
			{
				((ParentNode) pParent).getChildren().remove(pToRemove);
				// We don't reassing the parent of the child to null in case the operation
				// is undone, at which point we'll need to know who the parent was.
			}
			for( Node child : ((ParentNode) pParent).getChildren() )
			{
				removeFromParent(child, pToRemove );
			}
		}
	}

	/**
	 * Removes pElement from the graph.
	 * @param pElement The element to remove.
	 */
	public void removeElement(GraphElement pElement)
	{
		if(pElement instanceof Node)
		{
			removeNode((Node) pElement);
		}
		else if(pElement instanceof Edge)
		{

			removeEdge((Edge) pElement);
		}
	}

	/**
	 * @param pElement The element we want to check is in the grapgh.
	 * @return True if pElement is a node or edge in this graph.
	 */
	public boolean contains( GraphElement pElement )
	{	
		if( aEdges.contains( pElement ))
		{
			return true;
		}
		for( Node node : aRootNodes )
		{
			if( containsNode( node, pElement))
			{
				return true;
			}
		}
		return false;
	}
	
	private boolean containsNode(Node pTest, GraphElement pTarget)
	{
		if( pTest == pTarget )
		{
			return true;
		}
		else if( pTest instanceof ParentNode )
		{
			for( Node node : ((ParentNode) pTest).getChildren())
			{
				if( containsNode(node, pTarget))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Removes an edge from the graph.
	 * @param pEdge the edge to remove
	 */
	public void removeEdge(Edge pEdge)
	{
		if (aEdgesToBeRemoved.contains(pEdge))
		{
			return;
		}
		aEdgesToBeRemoved.add(pEdge);
		aModListener.edgeRemoved(this, pEdge);
		for(int i = aRootNodes.size() - 1; i >= 0; i--)
		{
			Node n = aRootNodes.get(i);
			if( n instanceof NoteEdge )
			{
				if(pEdge.getStart() == n)
				{
					removeNode(pEdge.getEnd());
				}
			}
		}
		aNeedsLayout = true;
	}

	/**
	 * Causes the layout of the graph to be recomputed.
	 */
	public void layout()
	{
		aNeedsLayout = true;
	}

	/**
	 * Computes the layout of the graph.
	 * If you override this method, you must first call 
	 * <code>super.layout</code>.
	 * @param pGraphics2D the graphics context
	 * @param pGrid the grid to snap to
	 */
	protected void layout(Graphics2D pGraphics2D, Grid pGrid)
	{
		if(!aNeedsLayout)
		{
			return;
		}
		aRootNodes.removeAll(aNodesToBeRemoved);
		aEdges.removeAll(aEdgesToBeRemoved);
		aNodesToBeRemoved.clear();
		aEdgesToBeRemoved.clear();

		for(int i = 0; i < aRootNodes.size(); i++)
		{
			Node n = aRootNodes.get(i);
			n.layout(this, pGraphics2D, pGrid);
		}
		aNeedsLayout = false;
	}

	/**
	 * Gets the smallest rectangle enclosing the graph.
	 * @return the bounding rectangle
	 */
	public Rectangle2D getBounds()
	{
		Rectangle2D r = aMinBounds;
		for(int i = 0; i < aRootNodes.size(); i++)
		{
			Node n = aRootNodes.get(i);
			Rectangle2D b = n.getBounds();
			if(r == null)
			{
				r = b;
			}
			else
			{
				r.add(b);
			}
		}
		for(int i = 0; i < aEdges.size(); i++)
		{
			Edge e = aEdges.get(i);
			r.add(e.getBounds());
		}
		if(r == null )
		{
			return new Rectangle2D.Double();
		}
		else
		{
			return new Rectangle2D.Double(r.getX(), r.getY(), r.getWidth() + AbstractNode.SHADOW_GAP, r.getHeight() + AbstractNode.SHADOW_GAP);
		}
	}

	/**
	 * @return The minimum bounds property
	 */
	public Rectangle2D getMinBounds() 
	{ return aMinBounds; }

	/**
	 * @param pMinBounds The minimum bounds property
	 */
	public void setMinBounds(Rectangle2D pMinBounds)
	{ aMinBounds = pMinBounds; }

	/**
	 * Gets the node types of a particular graph type.
	 * @return an array of node prototypes
	 */   
	public abstract Node[] getNodePrototypes();

	/**
	 * Gets the edge types of a particular graph type.
	 * @return an array of edge prototypes
	 */   
	public abstract Edge[] getEdgePrototypes();

	/**
	 * Adds a persistence delegate to a given encoder that
	 * encodes the child nodes of this graph.
	 * @param pEncoder the encoder to which to add the delegate
	 */
	public static void setPersistenceDelegate(Encoder pEncoder)
	{
		pEncoder.setPersistenceDelegate(Graph.class, new DefaultPersistenceDelegate()
		{
			protected void initialize(Class<?> pType, Object pOldInstance, Object pNewInstance, Encoder pOut) 
			{
				super.initialize(pType, pOldInstance, pNewInstance, pOut);
				Graph g = (Graph)pOldInstance;

				for(int i = 0; i < g.aRootNodes.size(); i++)
				{
					Node n = g.aRootNodes.get(i);
					Rectangle2D bounds = n.getBounds();
					Point2D p = new Point2D.Double(bounds.getX(), bounds.getY());
					pOut.writeStatement( new Statement(pOldInstance, "addNode", new Object[]{ n, p }) );
				}
				for(int i = 0; i < g.aEdges.size(); i++)
				{
					Edge e = g.aEdges.get(i);
					pOut.writeStatement( new Statement(pOldInstance, "connect", new Object[]{ e, e.getStart(), e.getEnd() }) );            
				}
			}
		});
	}

	/**
	 * Gets the nodes of this graph.
	 * @return an unmodifiable collection of the nodes
	 */
	public Collection<Node> getRootNodes()
	{ return aRootNodes; }

	/**
	 * Gets the edges of this graph.
	 * @return an unmodifiable collection of the edges
	 */
	public Collection<Edge> getEdges() 
	{ return aEdges; }

	/**
	 * Adds a node to this graph. This method should
	 * only be called by a decoder when reading a data file.
	 * @param pNode the node to add
	 * @param pPoint the desired location
	 */
	public void addNode(Node pNode, Point2D pPoint)
	{
		Rectangle2D bounds = pNode.getBounds();
		pNode.translate(pPoint.getX() - bounds.getX(), pPoint.getY() - bounds.getY()); 
		aRootNodes.add(pNode); 
	}

	/**
	 * Adds an edge to this graph. This method should
	 * only be called by a decoder when reading a data file or
	 * by testing code.
	 * @param pEdge the edge to add
	 * @param pStart the start node of the edge
	 * @param pEnd the end node of the edge
	 */
	public void connect(Edge pEdge, Node pStart, Node pEnd)
	{
		pEdge.connect(pStart, pEnd);
		aEdges.add(pEdge);
	}

	/**
	 * Checks whether it is legal to connect pNode1 to pNode2 through
	 * pEdge based strictly on the type of nodes and edges. 
	 * This implementation only provides the logic valid across
	 * all diagram types. Override for diagram-specific rules.
	 * @param pEdge The edge to be added
	 * @param pNode1 The first node
	 * @param pNode2 The second node
	 * @param pPoint2 The point where the edge is supposed to be terminated
	 * @return True if the edge can legally connect node1 to node2
	 */
	public boolean canConnect(Edge pEdge, Node pNode1, Node pNode2, Point2D pPoint2)
	{
		if( pNode2 == null )
		{
			return false;
		}
		if( existsEdge(pEdge.getClass(), pNode1, pNode2))
		{
			return false;
		}
		if((pNode2 instanceof NoteNode || pNode1 instanceof NoteNode) && !(pEdge instanceof NoteEdge))
		{
			return false;
		}
		if( pEdge instanceof NoteEdge && !(pNode1 instanceof NoteNode || pNode2 instanceof NoteNode))
		{
			return false;
		}
		return true;
	}
}



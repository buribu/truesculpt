package truesculpt.mesh;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.StringTokenizer;
import javax.microedition.khronos.opengles.GL10;

import android.graphics.Color;

import junit.framework.Assert;
import truesculpt.actions.ColorizeAction;
import truesculpt.actions.SculptAction;
import truesculpt.main.Managers;
import truesculpt.utils.MatrixUtils;
import truesculpt.utils.Utils;

public class Mesh
{
	ArrayList<Face> mFaceList = new ArrayList<Face>();
	ArrayList<Vertex> mVertexList = new ArrayList<Vertex>();	
	ArrayList<RenderFaceGroup> mRenderGroupList = new ArrayList<RenderFaceGroup>();
	OctreeNode mRootBoxNode=new OctreeNode(null, new float[]{0f,0f,0f}, 4f);
	Managers mManagers;

	public Mesh(Managers managers, int nSubdivisionLevel)
	{
		mManagers = managers;

		InitAsSphere(nSubdivisionLevel);

		mRootBoxNode.Vertices.addAll(mVertexList);
		mRootBoxNode.RecurseSubdivide();
		//CheckOctree();
		
		mRenderGroupList.add(new RenderFaceGroup(this));
	}
	
	private void CheckOctree()
	{
		//check all vertices have a box
		for (Vertex vertex : mVertexList)
		{
			Assert.assertTrue(vertex.Box!=null);
		}
		//count boxes
		ArrayList<OctreeNode> boxes=new ArrayList<OctreeNode>();
		RecurseBoxes(mRootBoxNode,boxes);
		int nVertexCount=0;
		int nNonEmptyBoxes=0;
		for(OctreeNode box : boxes)
		{
			int n=box.Vertices.size();
			nVertexCount+=n;
			if (n>0) { nNonEmptyBoxes++; }
		}
		Assert.assertTrue(nVertexCount==mVertexList.size());
	}
	
	private void RecurseBoxes(OctreeNode currBox, ArrayList<OctreeNode> boxes)
	{
		if (!currBox.IsLeaf())
		{
			boxes.addAll(currBox.NodeChilds);
			for (OctreeNode box : currBox.NodeChilds)
			{
				RecurseBoxes(box,boxes);
			}
		}
	}

	void ComputeAllVertexNormals()
	{
		int n = mVertexList.size();
		for (int i = 0; i < n; i++)
		{
			Vertex vertex = mVertexList.get(i);
			ComputeVertexNormal(vertex);
		}
	}
	
	float mBoundingSphereRadius = 0.0f;
	
	public void ComputeBoundingSphereRadius()
	{
		for (Vertex vertex : mVertexList)
		{
			float norm = MatrixUtils.magnitude(vertex.Coord);
			if (norm > mBoundingSphereRadius)
			{
				mBoundingSphereRadius = norm;
			}
		}
		getManagers().getPointOfViewManager().setRmin(1 + mBoundingSphereRadius);
	}

	// Based on close triangles normals * sin of their angle and normalize
	// averaging normals of triangles around
	public void ComputeVertexNormal(Vertex vertex)
	{
		//reset normal
		vertex.Normal[0]=0f;
		vertex.Normal[1]=0f;
		vertex.Normal[2]=0f;
		
		//not ordered
		for (HalfEdge edge: vertex.OutLinkedEdges)
		{			
			//optimize with prev/next in edge not face
			Assert.assertTrue(edge!=null);
			MatrixUtils.minus(mVertexList.get(edge.V1).Coord, mVertexList.get(edge.V0).Coord, u);
			
			HalfEdge otherEdge=mFaceList.get(edge.Face).GetPreviousEdge(edge);
			MatrixUtils.minus(mVertexList.get(otherEdge.V0).Coord,
							  mVertexList.get(otherEdge.V1).Coord,
							  v);

			MatrixUtils.cross(u, v, n); // cross product
			
			MatrixUtils.plus(n, vertex.Normal, vertex.Normal);
		}	
		
		//unit normal
		MatrixUtils.normalize(vertex.Normal);
	}
	
	//based on triangle only
	void ComputeFaceNormal(Face face, float[] normal)
	{
		// get triangle edge vectors and plane normal
		MatrixUtils.minus(mVertexList.get(face.E1.V0).Coord, mVertexList.get(face.E0.V0).Coord, u);
		MatrixUtils.minus(mVertexList.get(face.E2.V0).Coord, mVertexList.get(face.E0.V0).Coord, v);

		MatrixUtils.cross(u, v, n); // cross product
		MatrixUtils.normalize(n);

		MatrixUtils.copy(n, normal);
	}

	public void draw(GL10 gl)
	{
		for (RenderFaceGroup renderGroup : mRenderGroupList)
		{
			renderGroup.draw(gl);
		}		
	}
	
	public void drawNormals(GL10 gl)
	{
		for (RenderFaceGroup renderGroup : mRenderGroupList)
		{
			renderGroup.drawNormals(gl);
		}
	}
	
	public void drawOctree(GL10 gl)
	{
		ArrayList<OctreeNode> boxes=new ArrayList<OctreeNode>();
		RecurseBoxes(mRootBoxNode,boxes);		
		for(OctreeNode box : boxes)
		{
			if (!box.IsEmpty())
			{
				box.draw(gl);
			}
		}	
	}

	// From http://en.wikipedia.org/wiki/Wavefront_.obj_file
	public void ExportToOBJ(String strFileName)
	{
		try
		{
			BufferedWriter file = new BufferedWriter(new FileWriter(strFileName));

			file.write("#Generated by TrueSculpt version " + getManagers().getUpdateManager().getCurrentVersion().toString() + "\n");
			file.write("http://code.google.com/p/truesculpt/\n");

			file.write("\n");
			file.write("# List of Vertices, with (x,y,z[,w]) coordinates, w is optional\n");
			for (Vertex vertex : mVertexList)
			{
				String str = "v " + String.valueOf(vertex.Coord[0]) + " " + String.valueOf(vertex.Coord[1]) + " " + String.valueOf(vertex.Coord[2]) + "\n";
				file.write(str);
			}

			file.write("\n");
			file.write("# Texture coordinates, in (u,v[,w]) coordinates, w is optional\n");
			file.write("\n");

			file.write("# Normals in (x,y,z) form; normals might not be unit\n");
			for (Vertex vertex : mVertexList)
			{
				String str = "vn " + String.valueOf(vertex.Normal[0]) + " " + String.valueOf(vertex.Normal[1]) + " " + String.valueOf(vertex.Normal[2]) + "\n";
				file.write(str);
			}

			file.write("\n");
			file.write("# Face Definitions\n");
			for (Face face : mFaceList)
			{
				int n0 = face.E0.V0;
				int n1 = face.E1.V0;
				int n2 = face.E2.V0;

				assertTrue(n0 >= 0);
				assertTrue(n1 >= 0);
				assertTrue(n2 >= 0);

				// A valid vertex index starts from 1 and match first vertex element of vertex list previously defined. Each face can contain more than three elements.
				String str = "f " + String.valueOf(n0 + 1) + "//" + String.valueOf(n0 + 1) + " " + String.valueOf(n1 + 1) + "//" + String.valueOf(n1 + 1) + " " + String.valueOf(n2 + 1) + "//" + String.valueOf(n2 + 1) + "\n";

				file.write(str);
			}

			file.write("\n");
			file.close();

		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		getManagers().getUtilsManager().ShowToastMessage("Sculpture successfully exported to " + strFileName);
	}

	private void FinalizeSphereInit()
	{
		setAllVerticesColor(getManagers().getToolsManager().getDefaultColor());

		normalizeAllVertices();
		
		computeVerticesLinkedEdges();
		linkNeighbourEdges();
		
		checkFacesNormals();
	}

	private void checkFacesNormals()
	{
		// check triangle normals are outside and correct if necessary
		float[] u = new float[3];
		float[] v = new float[3];
		float[] n = new float[3];
		float[] dir = new float[3];

		for (Face face : mFaceList)
		{
			Vertex V0 = mVertexList.get(face.E0.V0);
			Vertex V1 = mVertexList.get(face.E1.V0);
			Vertex V2 = mVertexList.get(face.E2.V0);

			// get triangle edge vectors and plane normal
			MatrixUtils.minus(V1.Coord, V0.Coord, u);
			MatrixUtils.minus(V2.Coord, V0.Coord, v);

			MatrixUtils.cross(u, v, n); // cross product

			dir = V0.Coord;

			boolean bColinear = MatrixUtils.dot(dir, n) > 0;// dir and normal have same direction
			if (!bColinear)// swap two edges
			{
				assertTrue(false);
			}
		}
	}

	private void setAllVerticesColor(int color)
	{		
		for (Vertex vertex : mVertexList)
		{
			vertex.Color = color;
		}		
	}	

	public int getFaceCount()
	{
		return mFaceList.size();
	}

	public Managers getManagers()
	{
		return mManagers;
	}

	public int getVertexCount()
	{
		return mVertexList.size();
	}

	public void ImportFromOBJ(String strFileName) throws IOException
	{
		Reset();

		int nCount = 0;

		LineNumberReader input = new LineNumberReader(new InputStreamReader(new FileInputStream(strFileName)));
		String line = null;
		try
		{
			for (line = input.readLine(); line != null; line = input.readLine())
			{
				if (line.length() > 0)
				{
					if (line.startsWith("v "))
					{
						float[] coord = new float[3];
						StringTokenizer tok = new StringTokenizer(line);
						tok.nextToken();
						coord[0] = Float.parseFloat(tok.nextToken());
						coord[1] = Float.parseFloat(tok.nextToken());
						coord[2] = Float.parseFloat(tok.nextToken());
						mVertexList.add(new Vertex(coord));
					}
					else if (line.startsWith("vt "))
					{
						float[] coord = new float[2];
						StringTokenizer tok = new StringTokenizer(line);
						tok.nextToken();
						coord[0] = Float.parseFloat(tok.nextToken());
						coord[1] = Float.parseFloat(tok.nextToken());
						// m.addTextureCoordinate(coord);
					}
					else if (line.startsWith("f "))
					{
						int[] face = new int[3];
						int[] face_n_ix = new int[3];
						int[] face_tx_ix = new int[3];
						int[] val;

						StringTokenizer tok = new StringTokenizer(line);
						tok.nextToken();
						val = Utils.parseIntTriple(tok.nextToken());
						face[0] = val[0];
						if (val.length > 1 && val[1] > -1)
						{
							face_tx_ix[0] = val[1];
						}
						if (val.length > 2 && val[2] > -1)
						{
							face_n_ix[0] = val[2];
						}

						val = Utils.parseIntTriple(tok.nextToken());
						face[1] = val[0];
						if (val.length > 1 && val[1] > -1)
						{
							face_tx_ix[1] = val[1];
						}
						if (val.length > 2 && val[2] > -1)
						{
							face_n_ix[1] = val[2];
						}

						val = Utils.parseIntTriple(tok.nextToken());
						face[2] = val[0];
						if (val.length > 1 && val[1] > -1)
						{
							face_tx_ix[2] = val[1];
							// m.addTextureIndices(face_tx_ix);
						}
						if (val.length > 2 && val[2] > -1)
						{
							face_n_ix[2] = val[2];
							// m.addFaceNormals(face_n_ix);
						}
						
						mFaceList.add(new Face(face[0],face[1],face[2],mFaceList.size(),0));
						if (tok.hasMoreTokens())
						{
							val = Utils.parseIntTriple(tok.nextToken());
							face[1] = face[2];
							face[2] = val[0];
							if (val.length > 1 && val[1] > -1)
							{
								face_tx_ix[1] = face_tx_ix[2];
								face_tx_ix[2] = val[1];
								// m.addTextureIndices(face_tx_ix);
							}
							if (val.length > 2 && val[2] > -1)
							{
								face_n_ix[1] = face_n_ix[2];
								face_n_ix[2] = val[2];
								// m.addFaceNormals(face_n_ix);
							}
							mFaceList.add(new Face(face[0],face[1],face[2],mFaceList.size(),0));
						}

					} 
					else if (line.startsWith("vn "))
					{
						nCount++;
						float[] norm = new float[3];
						StringTokenizer tok = new StringTokenizer(line);
						tok.nextToken();
						norm[0] = Float.parseFloat(tok.nextToken());
						norm[1] = Float.parseFloat(tok.nextToken());
						norm[2] = Float.parseFloat(tok.nextToken());
						// m.addNormal(norm);
					}
				}
			}
		}
		catch (Exception ex)
		{
			System.err.println("Error parsing file:");
			System.err.println(input.getLineNumber() + " : " + line);
		}
		// if (!file_normal) {
		// m.calculateFaceNormals(coordinate_hand);
		// m.calculateVertexNormals();
		// // m.copyNormals();
		
		computeVerticesLinkedEdges();
		linkNeighbourEdges();
		
		ComputeAllVertexNormals();
		
		setAllVerticesColor(getManagers().getToolsManager().getDefaultColor());
		
		
		mRenderGroupList.add(new RenderFaceGroup(this));		
	}

	void InitAsIcosahedron()
	{
		float t = (float) ((1 + Math.sqrt(5)) / 2);
		float tau = (float) (t / Math.sqrt(1 + t * t));
		float one = (float) (1 / Math.sqrt(1 + t * t));

		mVertexList.add( new Vertex(tau, one, 0.0f));
		mVertexList.add( new Vertex(-tau, one, 0.0f));
		mVertexList.add( new Vertex(-tau, -one, 0.0f));
		mVertexList.add( new Vertex(tau, -one, 0.0f));
		mVertexList.add( new Vertex(one, 0.0f, tau));
		mVertexList.add( new Vertex(one, 0.0f, -tau));
		mVertexList.add( new Vertex(-one, 0.0f, -tau));
		mVertexList.add( new Vertex(-one, 0.0f, tau));
		mVertexList.add( new Vertex(0.0f, tau, one));
		mVertexList.add( new Vertex(0.0f, -tau, one));
		mVertexList.add( new Vertex(0.0f, -tau, -one));
		mVertexList.add( new Vertex(0.0f, tau, -one));

		// Counter clock wise (CCW) face definition
		mFaceList.add( new Face(4, 8, 7, mFaceList.size(),0));
		mFaceList.add( new Face(4, 7, 9, mFaceList.size(),0));
		mFaceList.add( new Face(5, 6, 11, mFaceList.size(),0));
		mFaceList.add( new Face(5, 10, 6, mFaceList.size(),0));
		mFaceList.add( new Face(0, 4, 3, mFaceList.size(),0));
		mFaceList.add( new Face(0, 3, 5, mFaceList.size(),0));
		mFaceList.add( new Face(2, 7, 1, mFaceList.size(),0));
		mFaceList.add( new Face(2, 1, 6, mFaceList.size(),0));
		mFaceList.add( new Face(8, 0, 11, mFaceList.size(),0));
		mFaceList.add( new Face(8, 11, 1, mFaceList.size(),0));
		mFaceList.add( new Face(9, 10, 3, mFaceList.size(),0));
		mFaceList.add( new Face(9, 2, 10, mFaceList.size(),0));
		mFaceList.add( new Face(8, 4, 0, mFaceList.size(),0));
		mFaceList.add( new Face(11, 0, 5, mFaceList.size(),0));
		mFaceList.add( new Face(4, 9, 3, mFaceList.size(),0));
		mFaceList.add( new Face(5, 3, 10, mFaceList.size(),0));
		mFaceList.add( new Face(7, 8, 1, mFaceList.size(),0));
		mFaceList.add( new Face(6, 1, 11, mFaceList.size(),0));
		mFaceList.add( new Face(7, 2, 9, mFaceList.size(),0));
		mFaceList.add( new Face(6, 10, 2, mFaceList.size(),0));

		assertEquals(mFaceList.size(), 20);
		assertEquals(mVertexList.size(), 12);

		// n_vertices = 12;
		// n_faces = 20;
		// n_edges = 30;
	}

	private void computeVerticesLinkedEdges()
	{
		//clear all
		for (Vertex vertex : mVertexList)
		{
			vertex.InLinkedEdges.clear();
			vertex.OutLinkedEdges.clear();
		}
		
		//compute all
		for (Face face : mFaceList)
		{
			UpdateVertexLinkedEdge(face.E0);
			UpdateVertexLinkedEdge(face.E1);
			UpdateVertexLinkedEdge(face.E2);
		}
	}
	
	private void UpdateVertexLinkedEdge(HalfEdge edge)
	{
		mVertexList.get(edge.V0).OutLinkedEdges.add(edge);
		mVertexList.get(edge.V1).InLinkedEdges.add(edge);		
	}
	
	//suppose linked edges of vertices are correct
	//suboptimal, liks made several times
	private void linkNeighbourEdges()
	{
		int n=mVertexList.size();
		for (int i=0;i<n;i++)
		{		
			Vertex vertex=mVertexList.get(i);
			for (HalfEdge e0 : vertex.OutLinkedEdges)
			{
				for (HalfEdge e1 : vertex.InLinkedEdges)
				{
					linkEdgesIfNeighbours(e0,e1);
				}
			}	
		}					
	}
	
	private boolean linkEdgesIfNeighbours(HalfEdge e0, HalfEdge e1)
	{
		boolean bRes=false;
		
		if ((e0.V0==e1.V1) && (e0.V1==e1.V0))
		{
			e0.NeighbourEdge=e1;
			e1.NeighbourEdge=e0;
			bRes=true;
		}
		
		return bRes;		
	}

	void InitAsSphere(int nSubdivionLevel)
	{
		Reset();
		InitAsIcosahedron();
		for (int i = 0; i < nSubdivionLevel; i++)
		{
			SubdivideAllFaces(i);			
		}		
		FinalizeSphereInit();		
	}

	// makes a sphere
	void normalizeAllVertices()
	{
		for (Vertex vertex : mVertexList)
		{
			MatrixUtils.normalize(vertex.Coord);
			MatrixUtils.copy(vertex.Coord, vertex.Normal);// Normal is coord because sphere is radius 1
		}
	}
	
	private void RecurseBoxesToTest(OctreeNode currBox, ArrayList<OctreeNode> BoxesToTest, final float[] Rinit, final float[] Rdir)
	{
		if (ray_box_intersect(currBox,Rinit,Rdir,0,10))
		{
			if (currBox.IsLeaf())
			{
				if(!currBox.IsEmpty())
				{			
					BoxesToTest.add(currBox);			
				}
			}
			else
			{			
				for (OctreeNode box : currBox.NodeChilds)
				{
					RecurseBoxesToTest(box,BoxesToTest,Rinit,Rdir);
				}
			}
		}
	}

	private void SortBoxesByDistance(ArrayList<OctreeNode> BoxesToTest, final float [] R0)
	{		
		Comparator<OctreeNode> comperator = new Comparator<OctreeNode>() 
		{
			@Override
			public int compare(OctreeNode box1, OctreeNode box2) 
			{
				float[] diff=new float[3];
				MatrixUtils.minus(box1.Center, R0, diff);
				float dist1=MatrixUtils.magnitude(diff);
				MatrixUtils.minus(box2.Center, R0, diff);
				float dist2=MatrixUtils.magnitude(diff);
				if (dist1<dist2)
				{
					return -1;
				}
				else if (dist1==dist2)
				{
					return 0;
				}
				else
				{
					return 1;					
				}
			}
		};
		Collections.sort(BoxesToTest, comperator);
	}
	
	ArrayList<OctreeNode> BoxesToTest=new ArrayList<OctreeNode>();
	HashSet <Integer> boxFaces= new HashSet <Integer>();
	public int Pick(float[] R0, float[] R1, float [] intersectPtReturn)
	{
		int nRes = -1;
		float[] Ires = new float[3];

		MatrixUtils.minus(R1, R0, dir);
		float fSmallestSqDistanceToR0 = MatrixUtils.squaremagnitude(dir);// ray is R0 to R1

		BoxesToTest.clear();
		RecurseBoxesToTest(mRootBoxNode,BoxesToTest,R0,dir);
		SortBoxesByDistance(BoxesToTest,R0);
		boxFaces.clear();
		for (OctreeNode box : BoxesToTest )
		{
			//fill face list of the box
			boxFaces.clear();
			for (Vertex vertex : box.Vertices)
			{
				for (HalfEdge edge : vertex.OutLinkedEdges)
				{
					boxFaces.add(edge.Face);
				}
			}
			
			//intersection with triangles of the box
			for (Integer i : boxFaces)
			{
				Face face = mFaceList.get(i);
				
				int nCollide = intersect_RayTriangle(R0, R1, mVertexList.get(face.E0.V0).Coord,  mVertexList.get(face.E1.V0).Coord,  mVertexList.get(face.E2.V0).Coord, Ires);
	
				if (nCollide == 1)
				{
					MatrixUtils.minus(Ires, R0, dir);
					float fSqDistanceToR0 = MatrixUtils.squaremagnitude(dir);
					if (fSqDistanceToR0 <= fSmallestSqDistanceToR0)
					{
						MatrixUtils.copy(Ires, intersectPtReturn);
						nRes = i;
						fSmallestSqDistanceToR0 = fSqDistanceToR0;
					}
				}
			}
			
			//intersection found stop loop
			if (nRes>=0)
			{
				break;
			}
		}
		return nRes;
	}
	
	// recycled vectors for time critical function where new are too long
	static float[] dir = new float[3];
	static float[] n = new float[3];
	static float SMALL_NUM = 0.00000001f; // anything that avoids division overflow
	static float[] u = new float[3];
	static float[] v = new float[3];
	static float[] w = new float[3];
	static float[] w0 = new float[3];
	static float[] zero = { 0, 0, 0 };

	// intersect_RayTriangle(): intersect a ray with a 3D triangle
	// Input: a ray R (R0 and R1), and a triangle T (V0,V1)
	// Output: *I = intersection point (when it exists)
	// Return: -1 = triangle is degenerate (a segment or point)
	// 0 = disjoint (no intersect)
	// 1 = intersect in unique point I1
	// 2 = are in the same plane
	static int intersect_RayTriangle(float[] R0, float[] R1, float[] V0, float[] V1, float[] V2, float[] Ires)
	{
		float r, a, b; // params to calc ray-plane intersect

		// get triangle edge vectors and plane normal
		MatrixUtils.minus(V1, V0, u);
		MatrixUtils.minus(V2, V0, v);

		MatrixUtils.cross(u, v, n); // cross product
		if (n == zero)
		{
			return -1; // do not deal with this case
		}

		MatrixUtils.minus(R1, R0, dir); // ray direction vector

		boolean bBackCullTriangle = MatrixUtils.dot(dir, n) > 0;// ray dir and normal have same direction
		if (bBackCullTriangle)
		{
			return 0;
		}

		MatrixUtils.minus(R0, V0, w0);
		a = -MatrixUtils.dot(n, w0);
		b = MatrixUtils.dot(n, dir);
		if (Math.abs(b) < SMALL_NUM)
		{ // ray is parallel to triangle plane
			if (a == 0)
			{
				return 2;
			} else
			{
				return 0; // ray disjoint from plane
			}
		}

		// get intersect point of ray with triangle plane
		r = a / b;
		if (r < 0.0)
		{
			return 0; // => no intersect
			// for a segment, also test if (r > 1.0) => no intersect
		}

		MatrixUtils.scalarMultiply(dir, r);
		MatrixUtils.plus(R0, dir, Ires);

		// is I inside T?
		float uu, uv, vv, wu, wv, D;
		uu = MatrixUtils.dot(u, u);
		uv = MatrixUtils.dot(u, v);
		vv = MatrixUtils.dot(v, v);
		MatrixUtils.minus(Ires, V0, w);
		wu = MatrixUtils.dot(w, u);
		wv = MatrixUtils.dot(w, v);
		D = uv * uv - uu * vv;

		// get and test parametric coords
		float s, t;
		s = (uv * wv - vv * wu) / D;
		if (s < 0.0 || s > 1.0)
		{
			return 0;
		}
		t = (uv * wu - uu * wv) / D;
		if (t < 0.0 || s + t > 1.0)
		{
			return 0;
		}

		return 1; // I is in T
	}
	
	public void InitGrabAction(int nTriangleIndex)
	{

	}	

	// TODO place as an action
	public void ColorizePaintAction(int triangleIndex)
	{
		if (triangleIndex >= 0)
		{
			int targetColor = getManagers().getToolsManager().getColor();						 
			Face face=mFaceList.get(triangleIndex);
			int nOrigVertex=face.E0.V0;//TODO choose closest point in triangle from pick point
			Vertex origVertex=mVertexList.get(nOrigVertex);
			float sqMaxDist=(float) Math.pow(getManagers().getToolsManager().getRadius()/100f+0.1f,2);
			float MaxDist=(float) Math.sqrt(sqMaxDist);
			HashSet <Integer> vertices=GetVerticesAtDistanceFromVertex(nOrigVertex,sqMaxDist);

			float [] VNewCol=new float[3];
			float [] VTargetCol=new float[3];
			Color.colorToHSV(targetColor, VTargetCol);
			float[] temp=new float[3];
			
			ColorizeAction action=new ColorizeAction();			
			for (Integer i : vertices)
			{
				Vertex vertex=mVertexList.get(i);
				
				MatrixUtils.minus(vertex.Coord, origVertex.Coord, temp);
				float dist=MatrixUtils.magnitude(temp);								

				Color.colorToHSV(vertex.Color, VNewCol);
				
				//barycenter of colors
				float alpha=(MaxDist-dist)/MaxDist;//[0;1]
				VNewCol[0]=VTargetCol[0];
				VNewCol[1]=VTargetCol[1];
				VNewCol[2]=(1-alpha)*VNewCol[2]+alpha*VTargetCol[2];
				
				int newColor=Color.HSVToColor(VNewCol);
				action.AddVertexColorChange(i, newColor, vertex);
			}
			getManagers().getActionsManager().AddUndoAction(action);
			action.DoAction();			
		}
	}	

	private HashSet <Integer> GetVerticesAtDistanceFromVertex(int nVertex, float sqDistance)
	{
		HashSet <Integer> res=new HashSet <Integer>();
		res.add(nVertex);//at at least this point
		Vertex origVertex=mVertexList.get(nVertex);
		
		//init testList
		ArrayList<Integer> verticesToTest=new ArrayList<Integer>();
		for (HalfEdge edge : origVertex.OutLinkedEdges)
		{
			verticesToTest.add(edge.V1);
		}
		
		float[] temp=new float[3];
		int nCount=verticesToTest.size();
		while (nCount>0)
		{			
			int nCurrIndex=verticesToTest.get(nCount-1);
			verticesToTest.remove(nCount-1);
			
			Vertex currVertex=mVertexList.get(nCurrIndex);
			MatrixUtils.minus(currVertex.Coord, origVertex.Coord, temp);
			float currSqDistance=MatrixUtils.squaremagnitude(temp);
			if (currSqDistance<sqDistance)
			{
				res.add(nCurrIndex);
				for (HalfEdge edge : currVertex.OutLinkedEdges)
				{
					int nToAdd=edge.V1;
					if (!res.contains(nToAdd))//avoids looping
					{
						verticesToTest.add(nToAdd);
					}
				}
			}
			
			nCount=verticesToTest.size();
		}		
		
		return res;		
	}
	
	private float FWHM=(float) (2f*Math.sqrt(2*Math.log(2f)));//full width at half maximum
	private float oneoversqrttwopi=(float) (1f/Math.sqrt(2f*Math.PI));
	
	// TODO place as an action
	public void RiseSculptAction(int triangleIndex)
	{
		if (triangleIndex >= 0)
		{			
			float fMaxDeformation = getManagers().getToolsManager().getStrength() / 100.0f * 0.2f;// strength is -100 to 100
			
			Face face=mFaceList.get(triangleIndex);
			int nOrigVertex=face.E0.V0;//TODO choose closest point in triangle from pick point
			Vertex origVertex=mVertexList.get(nOrigVertex);
			
			float sqMaxDist=(float) Math.pow(getManagers().getToolsManager().getRadius()/100f+0.1f,2);
			HashSet <Integer> vertices=GetVerticesAtDistanceFromVertex(nOrigVertex,sqMaxDist);
			float sigma=(float) ((Math.sqrt(sqMaxDist)/2f)/FWHM);

			// separate compute and apply of vertex pos otherwise compute is false
			SculptAction action=new SculptAction();			
			float[] VOffset = new float[3];
			float[] temp=new float[3];
			for (Integer i : vertices)
			{
				Vertex vertex=mVertexList.get(i);
				MatrixUtils.copy(origVertex.Normal, VOffset);
				//MatrixUtils.copy(vertex.Normal, VOffset);
				
				MatrixUtils.minus(vertex.Coord, origVertex.Coord, temp);
				float sqDist=MatrixUtils.squaremagnitude(temp);

				//sculpting functions				
				MatrixUtils.scalarMultiply(VOffset, (float) (oneoversqrttwopi/sigma*Math.exp(-sqDist/(2*sigma*sigma))*fMaxDeformation));
				action.AddVertexOffset(i,VOffset,vertex);		
			}
			getManagers().getActionsManager().AddUndoAction(action);
			action.DoAction();			
		}
	}

	//notification not done, to do in calling thread with post
	void Reset()
	{
		mVertexList.clear();
		mFaceList.clear();
		mRenderGroupList.clear();
		getManagers().getActionsManager().ClearAll(false);
	}

	//to share vertices between edges
	private int getMiddleDivideVertexForEdge(HalfEdge edge)
	{
		int nRes=-1;
		if (edge.VNextSplit!=-1)
		{
			nRes=edge.VNextSplit;
		}
		else
		{
			nRes=mVertexList.size();
			mVertexList.add(new Vertex(mVertexList.get(edge.V0),mVertexList.get(edge.V1)));// takes mid point			
		}		
		return nRes;		
	}
	
	//one triangle become four (cut on middle of each edge)
	void SubdivideAllFaces(int nSubdivionLevel)
	{
		computeVerticesLinkedEdges();
		linkNeighbourEdges();
		
		//backup original face list and create a brand new one (no face is kept all divided), vertices are only addes none is removed
		ArrayList<Face> mOrigFaceList = mFaceList;
		mFaceList=new ArrayList<Face>();
		
		for (Face face : mOrigFaceList)
		{
			int nA=face.E0.V0;
			int nB=face.E1.V0;
			int nC=face.E2.V0;		
			
			int nD=getMiddleDivideVertexForEdge(face.E0);
			int nE=getMiddleDivideVertexForEdge(face.E1);
			int nF=getMiddleDivideVertexForEdge(face.E2);

			mFaceList.add( new Face(nA, nD, nF, mFaceList.size(),nSubdivionLevel+1));
			mFaceList.add( new Face(nD, nB, nE, mFaceList.size(),nSubdivionLevel+1));
			mFaceList.add( new Face(nE, nC, nF, mFaceList.size(),nSubdivionLevel+1));
			mFaceList.add( new Face(nD, nE, nF, mFaceList.size(),nSubdivionLevel+1));
			
			//update next split of neighbours
			face.E0.NeighbourEdge.VNextSplit=nD;
			face.E1.NeighbourEdge.VNextSplit=nE;
			face.E2.NeighbourEdge.VNextSplit=nF;	
		}
	}
	
	void SetAllEdgesSubdivionLevel(int nLevel)
	{
		for (Face face : mFaceList)
		{
			face.E0.nSubdivionLevel=nLevel;
			face.E1.nSubdivionLevel=nLevel;
			face.E2.nSubdivionLevel=nLevel;		
		}
	}
	
	public void UpdateVertexValue(int nVertexIndex, Vertex vertex)
	{	
		vertex.Box.Reboxing(vertex);//update octree
		
		for (RenderFaceGroup renderGroup : mRenderGroupList)
		{
			renderGroup.UpdateVertexValue( nVertexIndex, vertex.Coord, vertex.Normal);
		}
		UpdateBoudingSphereRadius(vertex.Coord);				
	}
	
	public void UpdateVertexColor( int nVertexIndex, Vertex vertex)
	{
		for (RenderFaceGroup renderGroup : mRenderGroupList)
		{
			renderGroup.UpdateVertexColor( nVertexIndex, vertex.Color);
		}		
	}

	void UpdateBoudingSphereRadius(float[] val)
	{
		float norm = MatrixUtils.magnitude(val);
		if (norm > mBoundingSphereRadius)
		{
			mBoundingSphereRadius = norm;
			getManagers().getPointOfViewManager().setRmin(1 + mBoundingSphereRadius);// takes near clip into accoutn, TODO read from conf
		} 
	}

	public void PickColorAction(int nIndex)
	{
		if (nIndex >= 0)
		{			
			Face face=mFaceList.get(nIndex);
			Vertex vertex=mVertexList.get(face.E0.V0);//arbitrarily chosen point in triangle
			int color=vertex.Color;
			getManagers().getToolsManager().setColor(color, true);			
		}		
	}
	
	
	public ArrayList<Vertex> getVertexList()
	{
		return mVertexList;
	}

	public ArrayList<Face> getFaceList()
	{
		return mFaceList;
	}

	float square( float f ) { return (f*f) ;};

	 // x1,y1,z1  P1 coordinates (point of line)
	 // x2,y2,z2  P2 coordinates (point of line)
	 // x3,y3,z3, r  P3 coordinates and radius (sphere)
	boolean sphere_line_intersection (
	    float x1, float y1 , float z1,
	    float x2, float y2 , float z2,
	    float x_sphere, float y_sphere , float z_sphere, float r_sphere )
	{	
		 float a, b, c, i ;
	
		 a =  square(x2 - x1) + square(y2 - y1) + square(z2 - z1);
		 b =  2* ( (x2 - x1)*(x1 - x_sphere)
		      + (y2 - y1)*(y1 - y_sphere)
		      + (z2 - z1)*(z1 - z_sphere) ) ;
		 c =  square(x_sphere) + square(y_sphere) +
		      square(z_sphere) + square(x1) +
		      square(y1) + square(z1) -
		      2* ( x_sphere*x1 + y_sphere*y1 + z_sphere*z1 ) - square(r_sphere) ;
		 i =   b * b - 4 * a * c ;
	
		 if ( i < 0.0 )
		 {
			  // no intersection	 
			  return(false);
		 }		
		 else
		 {		
			 return true;
		 }
	}
	

	// Smits� method
	boolean ray_box_intersect(OctreeNode box, final float[] rayOrig, final float[] rayDir, float t0, float t1) 
	{
		float tmin, tmax, tymin, tymax, tzmin, tzmax;
		if (rayDir[0] >= 0) 
		{
			tmin = (box.Min[0] - rayOrig[0]) / rayDir[0];
			tmax = (box.Max[0] - rayOrig[0]) / rayDir[0];
		}
		else 
		{
			tmin = (box.Max[0] - rayOrig[0]) / rayDir[0];
			tmax = (box.Min[0] - rayOrig[0]) / rayDir[0];
		}
		if (rayDir[1] >= 0)
		{
			tymin = (box.Min[1] - rayOrig[1]) / rayDir[1];
			tymax = (box.Max[1] - rayOrig[1]) / rayDir[1];
		}
		else 
		{
			tymin = (box.Max[1] - rayOrig[1]) / rayDir[1];
			tymax = (box.Min[1] - rayOrig[1]) / rayDir[1];
		}
		if ( (tmin > tymax) || (tymin > tmax) )	return false;
		if (tymin > tmin)tmin = tymin;
		if (tymax < tmax) tmax = tymax;
		if (rayDir[2] >= 0) 
		{
			tzmin = (box.Min[2] - rayOrig[2]) / rayDir[2];
			tzmax = (box.Max[2] - rayOrig[2]) / rayDir[2];
		}
		else 
		{
			tzmin = (box.Max[2] - rayOrig[2]) / rayDir[2];
			tzmax = (box.Min[2] - rayOrig[2]) / rayDir[2];
		}
		if ( (tmin > tzmax) || (tzmin > tmax) )	return false;
		if (tzmin > tmin)tmin = tzmin;
		if (tzmax < tmax)tmax = tzmax;
		return ( (tmin < t1) && (tmax > t0) );
	}
	
	
}

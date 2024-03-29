package truesculpt.tools.sculpting;

import truesculpt.actions.SculptAction;
import truesculpt.main.Managers;
import truesculpt.main.R;
import truesculpt.mesh.HalfEdge;
import truesculpt.mesh.RenderFaceGroup;
import truesculpt.mesh.Vertex;
import truesculpt.tools.base.SculptingTool;
import truesculpt.utils.MatrixUtils;

public class SmoothTool extends SculptingTool
{
	public SmoothTool(Managers managers)
	{
		super(managers);
	}

	// TODO take radius into account and average further
	@Override
	protected void Work()
	{
		for (Vertex vertex : mVerticesRes)
		{
			// Place at average position of all surrounding points
			MatrixUtils.zero(VOffset);
			for (HalfEdge edge : vertex.OutLinkedEdges)
			{
				MatrixUtils.plus(mMesh.mVertexList.get(edge.V1).Coord, VOffset, VOffset);
			}
			MatrixUtils.scalarMultiply(VOffset, 1.0f / vertex.OutLinkedEdges.size());

			((SculptAction) mAction).AddNewVertexValue(VOffset, vertex);

			// preview
			MatrixUtils.copy(vertex.Normal, VNormal);
			MatrixUtils.scalarMultiply(VNormal, vertex.mLastTempSqDistance / mSquareMaxDistance);
			for (RenderFaceGroup renderGroup : mMesh.mRenderGroupList)
			{
				renderGroup.UpdateVertexValue(vertex.Index, VOffset, VNormal);
			}
		}
	}

	@Override
	public int GetIcon()
	{
		return R.drawable.smooth;
	}

	@Override
	public String GetName()
	{
		return "Smooth";
	}

	@Override
	public boolean RequiresStrength()
	{
		return false;
	}
}

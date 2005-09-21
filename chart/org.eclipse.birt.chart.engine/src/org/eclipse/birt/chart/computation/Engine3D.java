/*******************************************************************************
 * Copyright (c) 2004 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.chart.computation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.birt.chart.event.Image3DRenderEvent;
import org.eclipse.birt.chart.event.Line3DRenderEvent;
import org.eclipse.birt.chart.event.Oval3DRenderEvent;
import org.eclipse.birt.chart.event.Polygon3DRenderEvent;
import org.eclipse.birt.chart.event.Text3DRenderEvent;
import org.eclipse.birt.chart.event.WrappedInstruction;
import org.eclipse.birt.chart.model.attribute.Angle3D;
import org.eclipse.birt.chart.model.attribute.Location;
import org.eclipse.birt.chart.model.attribute.Location3D;
import org.eclipse.birt.chart.model.attribute.Rotation3D;
import org.eclipse.birt.chart.model.attribute.impl.BoundsImpl;
import org.eclipse.birt.chart.model.attribute.impl.LocationImpl;
import org.eclipse.birt.chart.util.Matrix;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Engine3D
 */
public final class Engine3D implements IConstants
{

	/**
	 * Indicates the both points are in range in clipping.
	 */
	public static final byte OUT_OF_RANGE_NONE = 0x0;
	/**
	 * Indicates the ending point is out of range in clipping.
	 */
	public static final byte OUT_OF_RANGE_END = 0x1;
	/**
	 * Indicates the starting point is out of range in clipping.
	 */
	public static final byte OUT_OF_RANGE_START = 0x2;
	/**
	 * Indicates the both points are out of range in clipping.
	 */
	public static final byte OUT_OF_RANGE_BOTH = 0x4;

	/**
	 * Viewer window size
	 */
	private double dViewerWidth, dViewerHeight;

	/**
	 * Viewer plane distance
	 */
	private double VIEW_DISTANCE = 200;
	/**
	 * Model plane distance
	 */
	private double MODEL_DISTANCE = 290;

	/**
	 * Front plane distance
	 */
	private double FRONT_DISTANCE = 20;
	/**
	 * Back plane distance
	 */
	private double BACK_DISTANCE = 600;

	/**
	 * Perspective distance
	 */
	private double PERSPECTIVE_VALUE = 100;

	/**
	 * Plane Reference Points
	 */
	private Vector[] PRP;
	/**
	 * Normal Vectors of Plane
	 */
	private Vector[] PNV;

	/**
	 * Center of projection
	 */
	private Vector COP;
	/**
	 * Projection direction unit vector
	 */
	private Vector VDZ;
	/**
	 * Up direction unit vector
	 */
	private Vector VDY;
	/**
	 * Right direction unit vector
	 */
	private Vector VDX;

	/**
	 * Light direction vector
	 */
	private Vector LDR;

	/**
	 * Rotation of the coordinate frame
	 */
	private Rotation3D ROT;

	/**
	 * Matrix to convert viewer coordinates to model coordinates.
	 */
	private Matrix V2M_MATRIX;
	/**
	 * Matrix to convert model coordinates to viewer coordinates.
	 */
	private Matrix M2V_MATRIX;
	/**
	 * Matrix to convert canvas coordinates to viewer coordinates.
	 */
	private Matrix C2V_MATRIX;
	/**
	 * Matrix to convert viewer coordinates to canvas coordinates.
	 */
	private Matrix V2C_MATRIX;

	/**
	 * @param rotation
	 * @param lightDirection
	 * @param viewerWidth
	 * @param viewerHeight
	 * @param viewingDistance
	 * @param hitherDistance
	 * @param yonDistance
	 */
	public Engine3D( Rotation3D rotation, Vector lightDirection,
			double viewerWidth, double viewerHeight, double viewingDistance,
			double modelingDistance, double hitherDistance, double yonDistance,
			double perspectiveDistance )
	{
		dViewerWidth = viewerWidth;
		dViewerHeight = viewerHeight;

		ROT = (Rotation3D) EcoreUtil.copy( rotation );
		LDR = new Vector( lightDirection );

		VIEW_DISTANCE = viewingDistance;
		MODEL_DISTANCE = modelingDistance;
		FRONT_DISTANCE = hitherDistance;
		BACK_DISTANCE = yonDistance;

		PERSPECTIVE_VALUE = perspectiveDistance;

		reset( );
	}

	/**
	 * @param rotation
	 * @param lightDirection
	 * @param viewerWidth
	 * @param viewerHeight
	 */
	public Engine3D( Rotation3D rotation, Vector lightDirection,
			double viewerWidth, double viewerHeight )
	{
		dViewerWidth = viewerWidth;
		dViewerHeight = viewerHeight;

		ROT = (Rotation3D) EcoreUtil.copy( rotation );
		LDR = new Vector( lightDirection );

		reset( );
	}

	/**
	 * Resets the engine to default state.
	 */
	public void reset( )
	{
		COP = new Vector( 0, 0, -MODEL_DISTANCE );

		VDX = new Vector( ( dViewerWidth / 2 ) / 100, 0, 0, false );
		VDY = new Vector( 0, ( dViewerHeight / 2 ) / 100, 0, false );
		VDZ = new Vector( 0, 0, VIEW_DISTANCE / 100, false );

		PNV = new Vector[6];
		PNV[0] = new Vector( 0, 0, 1, false ); // Hither Plane
		PNV[1] = new Vector( 0, 0, -1, false ); // Yon Plane
		PNV[2] = new Vector( 0, -1, 1, false ); // Top Plane
		PNV[3] = new Vector( 0, 1, 1, false ); // Bottom Plane
		PNV[4] = new Vector( 1, 0, 1, false ); // Left Plane
		PNV[5] = new Vector( -1, 0, 1, false ); // Right Plane

		PRP = new Vector[6];
		PRP[0] = new Vector( 0, 0, FRONT_DISTANCE ); // Hither Plane
		PRP[1] = new Vector( 0, 0, BACK_DISTANCE ); // Yon Plane
		PRP[2] = new Vector( 0, 0, 0 ); // Top Plane
		PRP[3] = new Vector( 0, 0, 0 ); // Bottom Plane
		PRP[4] = new Vector( 0, 0, 0 ); // Left Plane
		PRP[5] = new Vector( 0, 0, 0 ); // Right Plane

		V2M_MATRIX = Matrix.identity( 4, 4 );
		M2V_MATRIX = Matrix.identity( 4, 4 );
		initViewModelMatrix( );

		V2C_MATRIX = Matrix.identity( 4, 4 );
		C2V_MATRIX = Matrix.identity( 4, 4 );
		initViewCanvasMatrix( );

	}

	private void initViewModelMatrix( )
	{
		for ( int i = 0; i < 4; i++ )
		{
			V2M_MATRIX.set( 0, i, VDX.get( i ) );
			V2M_MATRIX.set( 1, i, VDY.get( i ) );
			V2M_MATRIX.set( 2, i, VDZ.get( i ) );
			V2M_MATRIX.set( 3, i, COP.get( i ) );
		}

		// M2V_MATRIX = inverse( V2M_MATRIX.copy( ) );
		M2V_MATRIX = V2M_MATRIX.copy( ).inverse( );
	}

	private void initViewCanvasMatrix( )
	{
		V2C_MATRIX.set( 0, 0, ( dViewerWidth / 2 ) / 100 );
		V2C_MATRIX.set( 1, 1, -( dViewerHeight / 2 ) / 100 );
		V2C_MATRIX.set( 3, 0, dViewerWidth / 2 );
		V2C_MATRIX.set( 3, 1, dViewerHeight / 2 );

		C2V_MATRIX = V2C_MATRIX.copy( ).inverse( );
	}

	/**
	 * @param v
	 * @return
	 */
	Vector model2View( Vector v )
	{
		return v.getMultiply( M2V_MATRIX );
	}

	/**
	 * @param v
	 * @return
	 */
	Vector view2model( Vector v )
	{
		return v.getMultiply( V2M_MATRIX );
	}

	/**
	 * @param v
	 * @return
	 */
	Vector canvas2View( Vector v )
	{
		return v.getMultiply( C2V_MATRIX );
	}

	/**
	 * @param v
	 * @return
	 */
	Vector view2Canvas( Vector v )
	{
		return v.getMultiply( V2C_MATRIX );
	}

	/**
	 * Translates the view frame.
	 * 
	 * @param v
	 */
	public void translate( Vector v )
	{
		COP.add( v );

		initViewModelMatrix( );
	}

	/**
	 * Rotates the view frame along X axis
	 * 
	 * @param degree
	 */
	/*
	 * public void rotateViewX( double degree ) { Matrix m = Matrix.identity( 4,
	 * 4 );
	 * 
	 * double radians = Math.toRadians( degree ); double cos = Math.cos( radians );
	 * double sin = Math.sin( radians );
	 * 
	 * m.set( 1, 1, cos ); m.set( 2, 2, cos ); m.set( 1, 2, -sin ); m.set( 2, 1,
	 * sin );
	 * 
	 * VDX.multiply( m ); VDY.multiply( m ); VDZ.multiply( m );
	 * 
	 * initViewModelMatrix( ); }
	 * 
	 * /** Rotates the view frame along Y axis
	 * 
	 * @param degree
	 */
	/*
	 * public void rotateViewY( double degree ) { Matrix m = Matrix.identity( 4,
	 * 4 );
	 * 
	 * double radians = Math.toRadians( degree ); double cos = Math.cos( radians );
	 * double sin = Math.sin( radians );
	 * 
	 * m.set( 0, 0, cos ); m.set( 2, 2, cos ); m.set( 0, 2, sin ); m.set( 2, 0,
	 * -sin );
	 * 
	 * VDX.multiply( m ); VDY.multiply( m ); VDZ.multiply( m );
	 * 
	 * initViewModelMatrix( ); }
	 * 
	 * /** Rotates the view frame along Z axis
	 * 
	 * @param degree
	 */
	/*
	 * public void rotateViewZ( double degree ) { Matrix m = Matrix.identity( 4,
	 * 4 );
	 * 
	 * double radians = Math.toRadians( degree ); double cos = Math.cos( radians );
	 * double sin = Math.sin( radians );
	 * 
	 * m.set( 0, 0, cos ); m.set( 1, 1, cos ); m.set( 0, 1, -sin ); m.set( 1, 0,
	 * sin ); // m.set( 1, 1, cos ); // m.set( 2, 2, cos ); // m.set( 1, 2, sin ); //
	 * m.set( 2, 1, -sin );
	 * 
	 * VDX.multiply( m ); VDY.multiply( m ); VDZ.multiply( m );
	 * 
	 * initViewModelMatrix( ); }
	 */
	Matrix rotateMatrixX( Matrix t, double degree )
	{
		Matrix m = Matrix.identity( 4, 4 );

		double radians = Math.toRadians( degree );
		double cos = Math.cos( radians );
		double sin = Math.sin( radians );

		m.set( 1, 1, cos );
		m.set( 2, 2, cos );
		m.set( 1, 2, sin );
		m.set( 2, 1, -sin );

		return t.times( m );
	}

	Matrix rotateMatrixY( Matrix t, double degree )
	{
		Matrix m = Matrix.identity( 4, 4 );

		double radians = Math.toRadians( degree );
		double cos = Math.cos( radians );
		double sin = Math.sin( radians );

		m.set( 0, 0, cos );
		m.set( 2, 2, cos );
		m.set( 0, 2, -sin );
		m.set( 2, 0, sin );

		return t.times( m );
	}

	Matrix rotateMatrixZ( Matrix t, double degree )
	{
		Matrix m = Matrix.identity( 4, 4 );

		double radians = Math.toRadians( degree );
		double cos = Math.cos( radians );
		double sin = Math.sin( radians );

		m.set( 0, 0, cos );
		m.set( 1, 1, cos );
		m.set( 0, 1, sin );
		m.set( 1, 0, -sin );

		return t.times( m );
	}

	Matrix translateMatrix( Matrix t, Vector v )
	{
		Matrix m = Matrix.identity( 4, 4 );
		m.set( 3, 0, v.get( 0 ) );
		m.set( 3, 1, v.get( 1 ) );
		m.set( 3, 2, v.get( 2 ) );

		return t.times( m );
	}

	/**
	 * Clipping the lines according to viewing volumn.
	 * 
	 * @param start
	 * @param end
	 * @return
	 */
	private byte checkClipping( Vector start, Vector end )
	{
		byte retval = OUT_OF_RANGE_NONE;
		Vector v1 = new Vector( );
		Vector v2 = new Vector( );
		Vector clip_ptr = new Vector( );

		// check for each plane, and do canonical view clipping
		for ( int i = 0; i < 6; i++ )
		{
			v1.set( start.get( 0 ), start.get( 1 ), start.get( 2 ) );
			v1.sub( PRP[i] );

			v2.set( end.get( 0 ), end.get( 1 ), end.get( 2 ) );
			v2.sub( PRP[i] );

			double sp1 = v1.scalarProduct( PNV[i] );
			double sp2 = v2.scalarProduct( PNV[i] );

			// both end point of line are out side of this clipping plane
			if ( sp1 < 0 && sp2 < 0 )
			{
				return OUT_OF_RANGE_BOTH;
			}

			// one end point is outside of the clipping plane, this point
			// needs to be clipped out
			if ( sp1 < 0 || sp2 < 0 )
			{
				double fraction = Math.abs( sp1 )
						/ ( Math.abs( sp1 ) + Math.abs( sp2 ) );
				clip_ptr.set( end.get( 0 ), end.get( 1 ), end.get( 2 ) );
				clip_ptr.sub( start );
				clip_ptr.scale( fraction );
				clip_ptr.add( start );

				// start point is clipped out, and is replaced by the new point
				if ( sp1 < 0 )
				{
					retval = (byte) ( retval | OUT_OF_RANGE_START );
					start.set( clip_ptr.get( 0 ),
							clip_ptr.get( 1 ),
							clip_ptr.get( 2 ) );
				}
				// end point is clipped out, and is replaced by the new point
				else
				{
					retval = (byte) ( retval | OUT_OF_RANGE_END );
					end.set( clip_ptr.get( 0 ),
							clip_ptr.get( 1 ),
							clip_ptr.get( 2 ) );
				}
			}
		}

		return retval;
	}

	/**
	 * @param va
	 * @param m
	 */
	void transform( Vector[] va, Matrix m )
	{
		for ( int i = 0; i < va.length; i++ )
		{
			va[i].multiply( m );
		}
	}

	/**
	 * @param va
	 * @return
	 */
	boolean checkBehindFace( Vector[] va )
	{
		Vector v1 = va[0];
		Vector v2 = va[1];
		Vector v3 = va[2];

		Vector u = new Vector( );
		Vector v = new Vector( );

		// get the normal vector of the face
		u.set( v1.get( 0 ), v1.get( 1 ), v1.get( 2 ) );
		u.sub( v2 );
		v.set( v2.get( 0 ), v2.get( 1 ), v2.get( 2 ) );
		v.sub( v3 );

		Vector uxv = u.crossProduct( v );

		Vector viewDirection = new Vector( v2.get( 0 ),
				v2.get( 1 ),
				v2.get( 2 ),
				false );

		// check if the normal vector of face points to the same direction
		// of the viewing direction
		return ( uxv.scalarProduct( viewDirection ) <= 0 );
	}

	/**
	 * Doing canonical view clipping for all the lines of the polygon
	 * 
	 * @param va
	 */
	Vector[] clipPolygon( Vector[] va )
	{
		byte retval;

		List lst = new ArrayList( );

		for ( int i = 0; i < va.length; i++ )
		{
			Vector start = null;
			Vector end = null;

			if ( i == va.length - 1 )
			{
				start = new Vector( va[i] );
				end = new Vector( va[0] );
			}
			else
			{
				start = new Vector( va[i] );
				end = new Vector( va[i + 1] );
			}

			retval = checkClipping( start, end );

			if ( retval != OUT_OF_RANGE_BOTH )
			{
				lst.add( start );
				lst.add( end );
			}
		}

		return (Vector[]) lst.toArray( new Vector[0] );
	}

	/**
	 * Doing canonical view clipping for all the lines of the polygon
	 * 
	 * @param va
	 */
	Vector[] clipLine( Vector[] va )
	{
		Vector start = new Vector( va[0] );
		Vector end = new Vector( va[1] );

		List lst = new ArrayList( );

		byte retval = checkClipping( start, end );

		if ( retval != OUT_OF_RANGE_BOTH )
		{
			lst.add( start );
			lst.add( end );
		}

		return (Vector[]) lst.toArray( new Vector[0] );
	}

	/**
	 * @param va
	 * @return
	 */
	Vector[] clipText( Vector[] va )
	{
		Vector start = new Vector( va[0] );
		Vector end = new Vector( va[0] );

		List lst = new ArrayList( );

		byte retval = checkClipping( start, end );

		if ( retval != OUT_OF_RANGE_BOTH )
		{
			lst.add( start );
		}

		return (Vector[]) lst.toArray( new Vector[0] );
	}

	/**
	 * @param va
	 * @return
	 */
	Vector[] clipImage( Vector[] va )
	{
		Vector start = new Vector( va[0] );
		Vector end = new Vector( va[0] );

		List lst = new ArrayList( );

		byte retval = checkClipping( start, end );

		if ( retval != OUT_OF_RANGE_BOTH )
		{
			lst.add( start );
		}

		return (Vector[]) lst.toArray( new Vector[0] );
	}

	/**
	 * Perspective transformation of the vectors.
	 * 
	 * @param va
	 * @param distance
	 */
	void perspective( Vector[] va, double distance )
	{
		for ( int i = 0; i < va.length; i++ )
		{
			va[i].perspective( distance );
		}
	}

	Matrix getTransformMatrix( )
	{
		Matrix m = Matrix.identity( 4, 4 );

		// inverse Z sign.
		m.set( 2, 2, -1 );

		for ( Iterator itr = ROT.getAngles( ).iterator( ); itr.hasNext( ); )
		{
			Angle3D agl = (Angle3D) itr.next( );
			if ( agl.getAxisType( ) == Angle3D.AXIS_NONE )
			{
				m = rotateMatrixY( m, agl.getYAngle( ) );
				m = rotateMatrixX( m, agl.getXAngle( ) );
				m = rotateMatrixZ( m, agl.getZAngle( ) );
			}
			else
			{
				switch ( agl.getAxisType( ) )
				{
					case Angle3D.AXIS_X :
						m = rotateMatrixX( m, agl.getAxisAngle( ) );
						break;
					case Angle3D.AXIS_Y :
						m = rotateMatrixY( m, agl.getAxisAngle( ) );
						break;
					case Angle3D.AXIS_Z :
						m = rotateMatrixZ( m, agl.getAxisAngle( ) );
						break;
				}
			}
		}

		// // TODO test translate matrix
		// Vector v = new Vector( 0, -200, 0, false );
		// // v.multiply( C2V_MATRIX );
		// // v.multiply( V2M_MATRIX );
		// m = translateMatrix( m, v );

		return m;
	}

	private Vector[] getVectors( Polygon3DRenderEvent evt )
	{
		Location3D[] loa = evt.getPoints3D( );

		Vector[] va = new Vector[loa.length];
		for ( int i = 0; i < va.length; i++ )
		{
			va[i] = new Vector( loa[i] );
		}
		return va;
	}

	private Vector[] getVectors( Oval3DRenderEvent evt )
	{
		Location3D[] loa = evt.getLocation3D( );

		Vector[] va = new Vector[loa.length];
		for ( int i = 0; i < va.length; i++ )
		{
			va[i] = new Vector( loa[i] );
		}
		return va;
	}

	private Vector[] getVectors( Line3DRenderEvent evt )
	{
		return new Vector[]{
				new Vector( evt.getStart3D( ) ), new Vector( evt.getEnd3D( ) )
		};
	}

	private Vector[] getVectors( Text3DRenderEvent evt )
	{
		if ( evt.getAction( ) == Text3DRenderEvent.RENDER_TEXT_AT_LOCATION )
		{
			return new Vector[]{
				new Vector( evt.getLocation3D( ) )
			};
		}
		else
		{
			// TODO process render-in-block.
			return new Vector[0];
		}
	}

	private Vector[] getVectors( Image3DRenderEvent evt )
	{
		return new Vector[]{
			new Vector( evt.getLocation3D( ) )
		};
	}

	private Location[] vectorArray2LocationArray( Vector[] va, double xOffset,
			double yOffset )
	{
		Location[] loa = new Location[va.length];
		for ( int i = 0; i < va.length; i++ )
		{
			loa[i] = vector2Location( va[i], xOffset, yOffset );
		}
		return loa;
	}

	private Location vector2Location( Vector v, double xOffset, double yOffset )
	{
		return LocationImpl.create( v.get( 0 ) + xOffset, v.get( 1 ) + yOffset );
	}

	private boolean translate3DEvent( Object obj, Matrix transMatrix,
			double xOffset, double yOffset )
	{
		if ( obj instanceof Polygon3DRenderEvent )
		{
			Polygon3DRenderEvent p3dre = (Polygon3DRenderEvent) obj;

			Vector[] va = getVectors( p3dre );

			transform( va, transMatrix );
			transform( va, M2V_MATRIX );

			boolean behind = checkBehindFace( va );
			p3dre.setBehind( behind );

			if ( !p3dre.isDoubleSided( ) && p3dre.isBehind( ) )
			{
				// optimize for culling face.
				// return false;
			}

			p3dre.updateNormal( va );
			double cosValue = p3dre.getNormal( ).cosineValue( LDR );
			if ( p3dre.isDoubleSided( ) )
			{
				cosValue = -Math.abs( cosValue );
			}
			double brightnessRatio = ( 1 - cosValue ) / 2d;
			p3dre.setBrightness( brightnessRatio );

			va = clipPolygon( va );
			perspective( va, PERSPECTIVE_VALUE );
			transform( va, V2C_MATRIX );

			Location[] loa = vectorArray2LocationArray( va, xOffset, yOffset );

			p3dre.updateCenter( va );

			p3dre.setPoints( loa );

			return true;// ( p3dre.isDoubleSided( ) || !p3dre.isBehind( ) );
		}
		else if ( obj instanceof Line3DRenderEvent )
		{
			Line3DRenderEvent l3dre = (Line3DRenderEvent) obj;

			if ( l3dre.getLineAttributes( ) == null
					|| !l3dre.getLineAttributes( ).isSetVisible( )
					|| !l3dre.getLineAttributes( ).isVisible( ) )
			{
				return false;
			}

			Vector[] va = getVectors( l3dre );

			transform( va, transMatrix );
			transform( va, M2V_MATRIX );

			va = clipLine( va );

			if ( va.length < 2 )
			{
				return false;
			}
			perspective( va, PERSPECTIVE_VALUE );
			transform( va, V2C_MATRIX );

			l3dre.updateCenter( va );

			l3dre.setStart( vector2Location( va[0], xOffset, yOffset ) );
			l3dre.setEnd( vector2Location( va[1], xOffset, yOffset ) );
		}
		else if ( obj instanceof Text3DRenderEvent )
		{
			Text3DRenderEvent t3dre = (Text3DRenderEvent) obj;

			Vector[] va = getVectors( t3dre );

			transform( va, transMatrix );
			transform( va, M2V_MATRIX );

			va = clipText( va );
			if ( va.length < 1 )
			{
				return false;
			}
			perspective( va, PERSPECTIVE_VALUE );
			transform( va, V2C_MATRIX );

			t3dre.updateCenter( va );

			t3dre.setLocation( vector2Location( va[0], xOffset, yOffset ) );

			if ( t3dre.getAction( ) == Text3DRenderEvent.RENDER_TEXT_IN_BLOCK )
			{
				t3dre.setAction( Text3DRenderEvent.RENDER_TEXT_AT_LOCATION );
			}
		}
		else if ( obj instanceof Oval3DRenderEvent )
		{
			Oval3DRenderEvent o3dre = (Oval3DRenderEvent) obj;

			Vector[] va = getVectors( o3dre );

			transform( va, transMatrix );
			transform( va, M2V_MATRIX );

			va = clipPolygon( va );
			if ( va.length < 3 )
			{
				return false;
			}
			perspective( va, PERSPECTIVE_VALUE );
			transform( va, V2C_MATRIX );

			o3dre.updateCenter( va );

			Location[] loa = vectorArray2LocationArray( va, xOffset, yOffset );
			o3dre.setBounds( BoundsImpl.create( loa[0].getX( ),
					loa[0].getY( ),
					loa[2].getX( ) - loa[1].getX( ),
					loa[0].getY( ) - loa[1].getY( ) ) );
		}
		else if ( obj instanceof Image3DRenderEvent )
		{
			Image3DRenderEvent i3dre = (Image3DRenderEvent) obj;

			Vector[] va = getVectors( i3dre );

			transform( va, transMatrix );
			transform( va, M2V_MATRIX );

			va = clipImage( va );
			if ( va.length < 1 )
			{
				return false;
			}
			perspective( va, PERSPECTIVE_VALUE );
			transform( va, V2C_MATRIX );

			i3dre.updateCenter( va );

			i3dre.setLocation( vector2Location( va[0], xOffset, yOffset ) );
		}

		return true;
	}

	/**
	 * @param renderingEvents
	 * @return
	 */
	public List processEvent( List renderingEvents, double xOffset,
			double yOffset )
	{
		Matrix transMatrix = getTransformMatrix( );

		List rtList = new ArrayList( );

		WrappedInstruction wi;

		for ( Iterator itr = renderingEvents.iterator( ); itr.hasNext( ); )
		{
			Object obj = itr.next( );

			wi = null;

			if ( obj instanceof WrappedInstruction )
			{
				wi = (WrappedInstruction) obj;

				// Never model for 3D events.
				assert !wi.isModel( );

				obj = wi.getEvent( );
			}

			if ( translate3DEvent( obj, transMatrix, xOffset, yOffset ) )
			{
				if ( wi != null )
				{
					rtList.add( wi );
				}
				else
				{
					rtList.add( obj );
				}
			}
		}

		// z-sort
		Collections.sort( rtList, new Comparator( ) {

			public int compare( Object o1, Object o2 )
			{
				double z1 = 0, z2 = 0;

				if ( o1 instanceof WrappedInstruction )
				{
					o1 = ( (WrappedInstruction) o1 ).getEvent( );
				}

				if ( o1 instanceof Polygon3DRenderEvent )
				{
					Vector v = ( (Polygon3DRenderEvent) o1 ).getCenter( );
					z1 = v.get( 2 );
				}
				else if ( o1 instanceof Line3DRenderEvent )
				{
					Vector v = ( (Line3DRenderEvent) o1 ).getCenter( );
					z1 = v.get( 2 );
				}
				else if ( o1 instanceof Text3DRenderEvent )
				{
					z1 = ( (Text3DRenderEvent) o1 ).getCenter( ).get( 2 );
				}
				else if ( o1 instanceof Oval3DRenderEvent )
				{
					Vector v = ( (Oval3DRenderEvent) o1 ).getCenter( );
					z1 = v.get( 2 );
				}
				else if ( o1 instanceof Image3DRenderEvent )
				{
					Vector v = ( (Image3DRenderEvent) o1 ).getCenter( );
					z1 = v.get( 2 );
				}
				else
				{
					return -1;
				}

				if ( o2 instanceof WrappedInstruction )
				{
					o2 = ( (WrappedInstruction) o2 ).getEvent( );
				}

				if ( o2 instanceof Polygon3DRenderEvent )
				{
					Vector v = ( (Polygon3DRenderEvent) o2 ).getCenter( );
					z2 = v.get( 2 );
				}
				else if ( o2 instanceof Line3DRenderEvent )
				{
					Vector v = ( (Line3DRenderEvent) o2 ).getCenter( );
					z2 = v.get( 2 );
				}
				else if ( o2 instanceof Text3DRenderEvent )
				{
					z2 = ( (Text3DRenderEvent) o2 ).getCenter( ).get( 2 );
				}
				else if ( o2 instanceof Oval3DRenderEvent )
				{
					Vector v = ( (Oval3DRenderEvent) o2 ).getCenter( );
					z2 = v.get( 2 );
				}
				else if ( o2 instanceof Image3DRenderEvent )
				{
					Vector v = ( (Image3DRenderEvent) o2 ).getCenter( );
					z2 = v.get( 2 );
				}
				else
				{
					return 1;
				}

				if ( z1 > z2 )
				{
					return -1;
				}
				else if ( z1 < z2 )
				{
					return 1;
				}

				return 0;
			}

		} );

		return rtList;
	}
}

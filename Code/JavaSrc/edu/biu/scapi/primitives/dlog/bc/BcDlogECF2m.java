/**
* This file is part of SCAPI.
* SCAPI is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
* SCAPI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with SCAPI.  If not, see <http://www.gnu.org/licenses/>.
*
* Any publication and/or code referring to and/or based on SCAPI must contain an appropriate citation to SCAPI, including a reference to http://crypto.cs.biu.ac.il/SCAPI.
*
* SCAPI uses Crypto++, Miracl, NTL and Bouncy Castle. Please see these projects for any further licensing issues.
*
*/
package edu.biu.scapi.primitives.dlog.bc;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Properties;

import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import edu.biu.scapi.primitives.dlog.DlogECF2m;
import edu.biu.scapi.primitives.dlog.ECElement;
import edu.biu.scapi.primitives.dlog.ECF2mUtility;
import edu.biu.scapi.primitives.dlog.GroupElement;
import edu.biu.scapi.primitives.dlog.groupParams.ECF2mGroupParams;
import edu.biu.scapi.primitives.dlog.groupParams.ECF2mKoblitz;
import edu.biu.scapi.primitives.dlog.groupParams.ECF2mPentanomialBasis;
import edu.biu.scapi.primitives.dlog.groupParams.ECF2mTrinomialBasis;
import edu.biu.scapi.primitives.dlog.groupParams.GroupParams;
import edu.biu.scapi.securityLevel.DDH;

/**
 * This class implements an Elliptic curve Dlog group over F2m utilizing Bouncy Castle's implementation. 
 * @author Cryptography and Computer Security Research Group Department of Computer Science Bar-Ilan University (Moriya Farbstein)
 *
 */
public class BcDlogECF2m extends BcAdapterDlogEC implements DlogECF2m, DDH{

	private ECF2mUtility util;
	
	/**
	 * Default constructor. Initializes this object with B-163 NIST curve.
	 */
	public BcDlogECF2m() throws IOException{
		this("B-163");
	}
	
	public BcDlogECF2m(String fileName, String curveName) throws IOException{
		super(fileName, curveName);
	}
	
	/**
	 * Constructor that initialize this DlogGroup with one of NIST recommended elliptic curve
	 * @param curveName - name of NIST curve to initialized
	 * @throws IOException 
	 * @throws IllegalAccessException
	 */
	public BcDlogECF2m(String curveName) throws IllegalArgumentException, IOException{
		this(NISTEC_PROPERTIES_FILE, curveName);
	}
	
	
	/*
	 * Extracts the parameters of the curve from the properties object and initialize the groupParams, 
	 * generator and the underlying curve. 
	 * @param ecProperties - properties object contains the curve file data
	 * @param curveName - the curve name as it is called in the file
	 */
	protected void doInit(Properties ecProperties, String curveName) {
		//Delegate the work on the params to the ECF2mUtility since this work does not depend on BC library. 
		util = new ECF2mUtility();
		groupParams = util.checkAndCreateInitParams(ecProperties, curveName);
		//Create a BC underlying curve:
		createUnderlyingCurveAndGenerator();
	}
	
	private void createUnderlyingCurveAndGenerator(){
		BigInteger x;
		BigInteger y;
		GroupParams params = groupParams;
		if (groupParams instanceof ECF2mKoblitz){
			params = ((ECF2mKoblitz) groupParams).getCurve();
		}
		if(params instanceof ECF2mTrinomialBasis){
			ECF2mTrinomialBasis triParams = (ECF2mTrinomialBasis)params;		
			curve = new ECCurve.F2m(triParams.getM(), triParams.getK1(), triParams.getA(), triParams.getB(), triParams.getQ(), triParams.getCofactor());
			x = triParams.getXg();
			y = triParams.getYg();
		}else{
			//we assume that if it's not trinomial then it's pentanomial. We do not check.
			ECF2mPentanomialBasis pentaParams = (ECF2mPentanomialBasis) params;
			curve = new ECCurve.F2m(pentaParams.getM(), pentaParams.getK1(), pentaParams.getK2(), pentaParams.getK3(),  pentaParams.getA(), pentaParams.getB(), pentaParams.getQ(), pentaParams.getCofactor());		
			x = pentaParams.getXg();
			y = pentaParams.getYg();
		}
		
		// create the generator
		// here we assume that (x,y) are the coordinates of a point that is indeed a generator
		generator = new ECF2mPointBc(x, y, this);
	}
	
	/**
	 * 
	 * @return the type of the group - ECF2m
	 */
	public String getGroupType(){
		return util.getGroupType();
	}
	
	/**
	 * Checks if the given element is a member of this Dlog group
	 * @param element 
	 * @return true if the given element is member of this group; false, otherwise.
	 * @throws IllegalArgumentException
	 */
	public boolean isMember(GroupElement element) throws IllegalArgumentException{
		
		if (!(element instanceof ECF2mPointBc)){
			throw new IllegalArgumentException("element type doesn't match the group type");
		}
		
		ECF2mPointBc point = (ECF2mPointBc) element;
		
		//infinity point is a valid member
		if (point.isInfinity()){
			return true;
		}
		
		// A point (x, y) is a member of a Dlog group with prime order q over an Elliptic Curve if it meets the following two conditions:
		// 1)	P = (x,y) is a point in the Elliptic curve, i.e (x,y) is a solution of the curve�s equation.
		// 2)	P = (x,y) is a point in the q-order group which is a sub-group of the Elliptic Curve.
		// those two checks is done in two steps:
		// 1.	Checking that the point is on the curve, performed by checkCurveMembership
		// 2.	Checking that the point is in the Dlog group,performed by checkSubGroupMembership

		boolean valid = util.checkCurveMembership((ECF2mGroupParams) groupParams, point.getX(), point.getY());
		valid = valid && util.checkSubGroupMembership(this, point);
		
		return valid;
			
	}
	
	/**
	 * Creates a point over F2m field with the given parameters
	 * @return the created point
	 */
	public ECElement generateElement(BigInteger x, BigInteger y) throws IllegalArgumentException{
		//Creates element with the given values.
		ECF2mPointBc point =  new ECF2mPointBc(x, y, this);
		
		//if the element was created, it is a point on the curve.
		//checks if the point is in the sub-group, too.
		boolean valid = util.checkSubGroupMembership(this, point);
		
		//if the point is not in the sub-group, throw exception.
		if (valid == false){
			throw new IllegalArgumentException("Could not generate the element. The given (x, y) is not a point in this Dlog group");
		}
		
		return point;
	}
	
	/**
	 * Creates ECPoint.F2m with the given parameters
	 */
	protected GroupElement createPoint(ECPoint result) {
		return new ECF2mPointBc(result);
	}
	
	/**
	 * Check if the element is valid to this elliptic curve group
	 */
	protected boolean checkInstance(GroupElement element){
		if (element instanceof ECF2mPointBc){
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Creates ECPoint.F2m with infinity values
	 */
	public ECElement getInfinity(){
		ECPoint infinity = curve.getInfinity();
		return new ECF2mPointBc(infinity);
	}
	
	/**
	 * Encode a byte array to an ECF2mPointBc. Some constraints on the byte array are necessary so that it maps into an element of this group.
	 * Currently we don't support this conversion. It will be implemented in the future.Meanwhile we return null
	 * @param binaryString the byte array to convert
	 * @return the created group Element
	 */
	public GroupElement encodeByteArrayToGroupElement(byte[] binaryString){
		//currently we don't support this conversion. 
		//will be implemented in the future.
		return null;
	}
	
	/**
	 * Decode an ECF2mPointBc that was obtained through the encodeByteArrayToGroupElement function to the original byte array.
	 * Currently we don't support this conversion. It will be implemented in the future.Meanwhile we return null.
	 * @param groupElement the element to convert
	 * @return the created byte array
	 */
	public byte[] decodeGroupElementToByteArray(GroupElement groupElement){
		if (!(groupElement instanceof ECF2mPointBc)){
			throw new IllegalArgumentException("element type doesn't match the group type");
		}
		//currently we don't support this conversion. 
		//will be implemented in the future.
		return null;
	}

	/**
	 * This function returns the value k which is the maximum length of a string to be converted to a Group Element of this group.<p>
	 * If a string exceeds the k length it cannot be converted
	 * Currently we do not have a proper algorithm for this therefore we return 0.
	 * @return k the maximum length of a string to be converted to a Group Element of this group
	 */
	public int getMaxLengthOfByteArrayForEncoding() {
		//Currently we do not have a proper algorithm for this.
		//Return 0
		return 0;
	}

	/**
	 * This is 1-1 mapping of any element of this group to a byte array representation.
	 * @param groupElement the element to convert
	 * @return the byte array representation
	 */
	public byte[] mapAnyGroupElementToByteArray(GroupElement groupElement) {
		//This function simply returns an array which is the result of concatenating 
		//the byte array representation of x with the byte array representation of y.
		if (!(groupElement instanceof ECF2mPointBc)) {
			throw new IllegalArgumentException("element type doesn't match the group type");
		}
		ECF2mPointBc point = (ECF2mPointBc) groupElement;
		//The actual work is implemented in ECF2mUtility since it is independent of the underlying library (BC, Miracl, or other)
		//If we ever decide to change the implementation there will only one place to change it.
		return util.mapAnyGroupElementToByteArray(point.getX(), point.getY());
	}


	

}

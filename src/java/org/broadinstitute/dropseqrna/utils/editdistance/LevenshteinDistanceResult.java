package org.broadinstitute.dropseqrna.utils.editdistance;

import htsjdk.samtools.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

public class LevenshteinDistanceResult {

	private static final Log log = Log.getInstance(LevenshteinDistanceResult.class);
	
	private int[][] distance = null;
	private String stringOne;
	private String stringTwo;
	private int deletionCost;
	private int insertionCost;
	private int substitutionCost;
	
	LevenshteinDistanceResult(String stringOne, String stringTwo, int [] [] distance, int deletionCost, int insertionCost, int substitutionCost) {
		this.distance = distance;	
		this.deletionCost= deletionCost;
		this.insertionCost=insertionCost;
		this.substitutionCost=substitutionCost;
	}
	
	public int getEditDistance () {
		int l1 = distance.length-1;
		int l2 = distance[0].length-1;
		return (this.distance[l1][l2]); 
	}
	
	/**
	 * After filling out the edit distance matrix, look at the set of operations.  
	 * Parse out the number of substitutions, and count each as ed+1.  Each PAIR of insertion/deletions counts for 1 ed change.
	 * Only use this if you set the substitution cost to be 2, and the deletion/insertion costs to be 1.
	 * If you run this on data that has a different substitution/deletion/insertion costs than the expected [2/1/1] then it re-runs populating the edit distance matrix with those values.  
	 * @return the edit distance corrected for indels on barcodes. 
	 */
	public Integer getEditDistanceIndelCorrected() {
		String [] ops=null;
		if (this.substitutionCost!=2 || this.deletionCost!=1 || this.insertionCost!=1) {
			log.info("Edit distances I expected were [2/1/1] for substitution,insertion,deletion.  You had [" + this.substitutionCost + "/"+ this.insertionCost + "/" + this.deletionCost+". Recalculating with expected costs.");
			LevenshteinDistanceResult r = LevenshteinDistance.computeLevenshteinDistanceResult(this.stringOne, this.stringTwo, 1,1,2);
			ops = getOperations(r.distance);
		} else {
			ops = getOperations();
		}
		// just count the indels
		int count=0;
		for (String o: ops) {
			switch (o) {
				case "S": count+=this.substitutionCost; break;
				case "I": count+=this.deletionCost; break;
				case "D": count+=this.insertionCost; break;
				case "M": count+=0; break;
			}
		} 
		return count/this.substitutionCost;
	}
	
	
	/**
	 * Get the set of operations to convert the first string to the second.
	 * There are actually multiple paths one can take that result in multiple paths.  I'd really like to take all equally valid paths and make a list of list of operations
	 *   
	 * @return
	 */
	public String [] getOperations() {
		return getOperations(this.distance);
	}
	
	/**
	 * Get the set of operations to convert the first string to the second.
	 * There are actually multiple paths one can take that result in multiple paths.  I'd really like to take all equally valid paths and make a list of list of operations
	 *   
	 * @return
	 */
	private String [] getOperations (int [] []  distance) {
		int l1 = distance.length-1;
		int l2 = distance[0].length-1;
		List<String> operations = new ArrayList<String>();
		
		int rowIndex=l1;
		int colIndex=l2;
		
		while (rowIndex>=0 && colIndex>=0) {
			if (rowIndex==0 && colIndex==0) break;			
			int current = AdjacentScores.getCurrentScore(rowIndex, colIndex, distance);
			int diagonal= AdjacentScores.getDiagonalScore(rowIndex, colIndex, distance);
			int up = AdjacentScores.getUpScore(rowIndex, colIndex, distance);
			int left = AdjacentScores.getLeftScore(rowIndex, colIndex, distance);
			
			// I think given an equal path to a deletion or a substitution, a deletion is preferred.
			//if (diagonal==current & diagonal<=left & diagonal <= up) {
			if (diagonal==current & diagonal<=left & diagonal <= up) {
				operations.add("M");
				rowIndex--;
				colIndex--;
			}
			else if (up<= diagonal & up<=current & left > up) {
				operations.add("D");
				
				rowIndex--;
			}// move diagonal as a sub or match
			else if (diagonal<= up && diagonal <= left && diagonal < current) {
				//substitution
				operations.add("S"); 
				rowIndex--;
				colIndex--;
			}
			// insertion
			else if (left < diagonal && left <= current) {
				operations.add("I");
				colIndex--;
			}
			
		}
		
		// flip the ordering so it's start to end.
		Collections.reverse(operations);
		String [] result = operations.toArray(new String [operations.size()]);
		
		return (result);
		
	}
	
	/**
	 * A class that defines the scores at a position in the matrix and adjacent scores
	 * This is purely made for reuse.
	 */
	private static class AdjacentScores {
		
		@SuppressWarnings("unused")
		public enum Operation { 
			M("M"),S("S"),I("I"),D("D");
			
			private final String name;
			Operation (String name) {
				this.name=name;
			}
			public String getName () {
				return this.name;
			}
		}
		
		static int getCurrentScore (int rowIndex, int colIndex, int distance [] []) {
			return distance[rowIndex][colIndex];
		}
		
		static int getUpScore(int rowIndex, int colIndex, int distance [] []) {
			// have to protect against running into the edges of the matrix.			
			int up =Math.max(rowIndex, colIndex);			
			if (rowIndex>0) up = distance[rowIndex-1][colIndex];
			return up;
		}
		
		static int getLeftScore(int rowIndex, int colIndex, int distance [] []) {
			// have to protect against running into the edges of the matrix.
			int left=Math.max(rowIndex, colIndex);
			if (colIndex>0) left = distance[rowIndex][colIndex-1];			
			return left;
		}
		
		static int getDiagonalScore(int rowIndex, int colIndex, int distance [] []) {
			// have to protect against running into the edges of the matrix.
			int diagonal=Math.max(rowIndex, colIndex);
			if (rowIndex>0 && colIndex>0) diagonal = distance[rowIndex-1][colIndex-1];			
			return diagonal;
		}
				
	}
	/**
	 * This gets all possible sets of edit distance operations along valid paths of decreasing edit distance.
	 * THIS IS NOT YET READY FOR PRIME TIME, BUT IS CLOSE.
	 * @return
	 */
	@SuppressWarnings("unused")
	private List<String [] > getAllOperationsMultiPath () {
		int l1 = distance.length-1;
		int l2 = distance[0].length-1;
		
		// initialize at the bottom corner of the matrix.
		Operations o = new Operations(l1, l2);
		List<Operations> searchList = new ArrayList<Operations>();
		searchList.add(o);
		
		Set<Operations> finalSet = new HashSet<Operations>();
		
		
		// start at the bottom corner of the matrix, and loop until all OperationList objects have climbed to 0,0.
		while (!searchList.isEmpty()) {
			List<Operations> searchListNext = new ArrayList<Operations>();
			for (Operations ol: searchList) {
				// if the element is done...
				if (ol.rowIndex==0 && ol.colIndex==0) {
					finalSet.add(ol);
				} else { // searching...
					List<Operations> iterResult = getOperations(ol);
					searchListNext.addAll(iterResult);	
				}
			}
			searchList=searchListNext;
		}
		
		List<String [] > done = new ArrayList<String []>();
		
		return done;
		
	}
	
	List<Operations> getOperations (Operations ol) {	
		List<Operations> result = new ArrayList<Operations>();
		
		int rowIndex=ol.rowIndex;
		int colIndex=ol.colIndex;
		
		int l1 = distance.length-1;
		int l2 = distance[0].length-1;

		// have to protect against running into the edges of the matrix.
		int diagonal=Math.max(l1, l2);
		int up =Math.max(l1, l2);
		int left=Math.max(l1, l2);
		if (rowIndex>0 && colIndex>0) {
			diagonal = distance[rowIndex-1][colIndex-1];
		}
		
		if (rowIndex>0) {
			up = distance[rowIndex-1][colIndex];
		}
		if (colIndex>0) {
			left = distance[rowIndex][colIndex-1];
		}
		
		// if there's more than one path to pick, we need to clone the current list of operations.
		// this is why there is one path handed in, and can be up to 3 paths out.
		
		int min=Math.min(Math.min(diagonal, up), left);
		
		if (min==diagonal) {
			Operations r = ol.clone();
			r.addOperation("M");
			r.setXY(rowIndex-1, colIndex-1);
			result.add(r);
		}
		
		if (min==up) {
			Operations r = ol.clone();
			r.addOperation("D");
			r.setXY(rowIndex-1, colIndex);
			result.add(r);
			
		}
		
		if (min==left) {
			Operations r = ol.clone();
			r.addOperation("I");
			r.setXY(rowIndex, colIndex-1);
			result.add(r);
		}
		return (result);
	}
	
	private class Operations {
		private int rowIndex;
		private int colIndex;
		private List<String> operations;
		
		public Operations(int rowIndex, int colIndex) {
			this.rowIndex=rowIndex;
			this.colIndex = colIndex;
			this.operations= new ArrayList<String>();
		}
		
		public void setXY (int rowIndex, int colIndex) {
			this.rowIndex=rowIndex;
			this.colIndex=colIndex;
		}
		
		public void addOperation(String op) {
			this.operations.add(op);
		}
		
		public Operations clone () {
			Operations r = new Operations(this.rowIndex, this.colIndex);
			for (String o: operations) {
				r.addOperation(new String (o));
			}
			return (r);
		}
		
		/**
		 * Operations are returned in the reverse order that they were generated.
		 * You iterate from the end of the strings to the beginning to trace back the edit distance operations
		 * This flips that so you can read from the front.
		 * @return
		 */
		@SuppressWarnings("unused")
		public String [] getOperations () {
			String [] r = new String [operations.size()];
			int counter=r.length-1;
			for (String s: operations) {
				r[counter]=s;
				counter--;
			}
			return r;
		}
		
		public String toString () {
			StringBuilder b = new StringBuilder();
			b.append("[" + this.rowIndex+","+this.colIndex+"]" );
			String h = StringUtils.join(this.operations, " ");
			b.append(h);
			return b.toString();
		}
		
		@Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Operations that = (Operations) o;
            
            if (this.operations.size()!= that.operations.size()) return false;
            for (int i=0; i<this.operations.size(); i++) {
            	if (!this.operations.get(i).equals(that.operations.get(i))) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
        	int result = 31;
        	for (String s: this.operations) {
        		result = 31 * result + s.hashCode();
        	}
            return result;
        }
		
	}
	
	
	
 	
}
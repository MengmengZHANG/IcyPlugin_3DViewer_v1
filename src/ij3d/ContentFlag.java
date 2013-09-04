package ij3d;

public class ContentFlag {
	private int posT;
	private int posC;
	private int sizeC;

	public ContentFlag(int posT, int posC) {
		this.posT = posT;
		this.posC = posC;
	}

	public ContentFlag(String name) {
		int indexT = 0;
		int indexC = name.indexOf("c");
		this.posT = Integer.parseInt(name.substring(indexT + 1, indexC));
		this.posC = Integer.parseInt(name.substring(indexC + 1));

	}

	public int getPosT() {
		return posT;
	}

	public int getSizeC() {
		return sizeC;
	}

	public void setPosT(int posT) {
		this.posT = posT;
	}

	public void setPosC(int posC) {
		this.posC = posC;
	}

	public void setSizeC(int sizeC) {
		this.sizeC = sizeC;
	}

	public int getPosC() {
		return posC;
	}

	public String getContentName() {
		return "t" + this.posT + "c" + this.posC;
	}

}

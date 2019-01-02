package scrapper;

public class ScrappingException extends Exception {
	private static final long serialVersionUID = 7717331644599259456L;

	public ScrappingException() {
		super();
	}
	public ScrappingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
	public ScrappingException(String message, Throwable cause) {
		super(message, cause);
	}
	public ScrappingException(String message) {
		super(message);
	}
	public ScrappingException(Throwable cause) {
		super(cause);
	}
}

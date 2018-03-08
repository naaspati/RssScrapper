package scrapper.scrapper;

public class NothingFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public NothingFoundException() {
        super();
    }
    public NothingFoundException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public NothingFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public NothingFoundException(String message) {
        super(message);
    }

    public NothingFoundException(Throwable cause) {
        super(cause);
    }

    
    

}

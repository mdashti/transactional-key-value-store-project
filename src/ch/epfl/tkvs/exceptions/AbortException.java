package ch.epfl.tkvs.exceptions;

public abstract class AbortException extends Exception {

    private static final long serialVersionUID = 1933382539780654321L;

    public AbortException(String string) {
        super(string);
    }

}

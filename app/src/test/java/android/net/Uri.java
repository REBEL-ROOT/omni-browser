package android.net;

public abstract class Uri {
    private final String uriString;

    protected Uri(String uriString) {
        this.uriString = uriString;
    }

    public static Uri parse(String uriString) {
        return new StringUri(uriString);
    }

    public abstract String getScheme();
    public abstract String getHost();
    public abstract Builder buildUpon();

    @Override
    public String toString() {
        return uriString;
    }

    private static class StringUri extends Uri {
        private java.net.URI javaUri;

        private StringUri(String uriString) {
            super(uriString);
            if (uriString != null) {
                try {
                    String normalized = uriString.trim();
                    if (normalized.startsWith("moz-extension://")) {
                        normalized = normalized.replace("moz-extension://", "http://");
                    }
                    this.javaUri = new java.net.URI(normalized);
                } catch (Exception e) {
                    this.javaUri = null;
                }
            }
        }

        @Override
        public String getScheme() {
            if (javaUri != null && javaUri.getScheme() != null) {
                return javaUri.getScheme();
            }
            String str = toString();
            if (str != null && str.contains(":")) {
                return str.split(":")[0];
            }
            return null;
        }

        @Override
        public String getHost() {
            if (javaUri != null && javaUri.getHost() != null) {
                return javaUri.getHost();
            }
            String str = toString();
            if (str == null) return null;
            try {
                if (str.startsWith("https://")) {
                    str = str.substring(8);
                } else if (str.startsWith("http://")) {
                    str = str.substring(7);
                } else if (str.startsWith("moz-extension://")) {
                    str = str.substring(16);
                } else if (str.startsWith("intent://")) {
                    str = str.substring(9);
                }
                int slash = str.indexOf('/');
                if (slash != -1) {
                    str = str.substring(0, slash);
                }
                int question = str.indexOf('?');
                if (question != -1) {
                    str = str.substring(0, question);
                }
                int hash = str.indexOf('#');
                if (hash != -1) {
                    str = str.substring(0, hash);
                }
                int colon = str.indexOf(':');
                if (colon != -1) {
                    str = str.substring(0, colon);
                }
                int semicolon = str.indexOf(';');
                if (semicolon != -1) {
                    str = str.substring(0, semicolon);
                }
                int at = str.lastIndexOf('@');
                if (at != -1) {
                    str = str.substring(at + 1);
                }
                if (str.endsWith(".")) {
                    str = str.substring(0, str.length() - 1);
                }
                return str.isEmpty() ? null : str;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public Builder buildUpon() {
            return new Builder(toString());
        }
    }

    public static class Builder {
        private String uriString;

        public Builder(String uriString) {
            this.uriString = uriString;
        }

        public Builder fragment(String fragment) {
            if (fragment == null && uriString != null && uriString.contains("#")) {
                uriString = uriString.split("#")[0];
            }
            return this;
        }

        public Uri build() {
            return new StringUri(uriString);
        }
    }
}

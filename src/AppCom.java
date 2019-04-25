public class AppCom {
    String toClient;
    String operatingClients;

    public AppCom(String id, String clients) {
        this.toClient = id;
        this.operatingClients = clients;
    }

    public String getToClient() {
        return toClient;
    }

    public void setToClient(String toClient) {
        this.toClient = toClient;
    }

    public String getOperatingClients() {
        return operatingClients;
    }

    public void setOperatingClients(String operatingClients) {
        this.operatingClients = operatingClients;
    }

}

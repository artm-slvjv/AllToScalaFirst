package tinkoff.all.to.scala.first;

public class DefaultClient implements Client {
    @Override
    public Response getApplicationStatus1(String id) {
        return new Response.Success("success", id);
    }

    @Override
    public Response getApplicationStatus2(String id) {
        return new Response.Failure(new RuntimeException("any service exception"));
    }
}

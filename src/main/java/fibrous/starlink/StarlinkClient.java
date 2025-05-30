package fibrous.starlink;

import java.text.ParseException;
import java.util.concurrent.TimeUnit;

import SpaceX.API.Device.DeviceGrpc;
import SpaceX.API.Device.DeviceOuterClass;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse;
import SpaceX.API.Device.DeviceOuterClass.GetDiagnosticsRequest;
import SpaceX.API.Device.DeviceOuterClass.Response;
import SpaceX.API.Device.DeviceOuterClass.ToDevice;
import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;

public class StarlinkClient {
	
	DeviceGrpc.DeviceBlockingStub blockingStub;
	ManagedChannel channel;

	String dishSocketAddr = "192.168.100.1:9200";
	String routerSocketAddr = "192.168.1.1:9000";
	String testSocketAddr = "localhost:9200";
	
	public StarlinkClient() {
		channel = Grpc.newChannelBuilder(testSocketAddr, InsecureChannelCredentials.create()).build();
		blockingStub = DeviceGrpc.newBlockingStub(channel);
	}
	
	public DishGetDiagnosticsResponse getDishDiagnostics() {
		ToDevice diagReq = ToDevice.newBuilder()
				.setRequest(DeviceOuterClass.Request
						.newBuilder()
						.setGetDiagnostics(GetDiagnosticsRequest.newBuilder()
								.build())
						.build())
				.build();
		
		Response diagResp = blockingStub.handle(diagReq.getRequest());
		
		return diagResp.getDishGetDiagnostics();
	}
}

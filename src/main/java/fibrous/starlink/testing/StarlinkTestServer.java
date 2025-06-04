/*
BSD 3-Clause License

Copyright (c) 2025 Noah McLean

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package fibrous.starlink.testing;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import SpaceX.API.Device.*;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.Alerts;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.AlignmentStats;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.DisablementCode;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.Location;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.TestResult;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.TestResultCode;
import SpaceX.API.Device.DeviceOuterClass.GetDiagnosticsRequest;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.protobuf.ProtoUtils;

public class StarlinkTestServer {
	
	private Server server;
	
	private void start() throws IOException {
		int routerPort = 9000;
		int dishPort = 9200;
		
		ExecutorService executor = Executors.newFixedThreadPool(2);
		server = Grpc.newServerBuilderForPort(dishPort, InsecureServerCredentials.create()).executor(executor).addService(new DeviceImpl()).build().start();
		
		System.out.println("Starlink Test Server started");
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			
			@Override
			public void run() {
				try {
					StarlinkTestServer.this.stop();
				} catch(InterruptedException e) {
					if(server != null) {
						server.shutdownNow();
					}
					e.printStackTrace();
				} finally {
					executor.shutdown();
				}
			}
		});
	}
	
	private void stop() throws InterruptedException {
		if(server != null) {
			server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
		}
	}
	
	private void blockUntilShutdown() throws InterruptedException {
		if(server != null) {
			server.awaitTermination();
		}
	}
	
	public static void main(String[]args) throws IOException, InterruptedException {
		StarlinkTestServer server = new StarlinkTestServer();
		server.start();
		server.blockUntilShutdown();
	}
	
	static class DeviceImpl extends DeviceGrpc.DeviceImplBase {
		
		@Override
		public void handle(DeviceOuterClass.Request request, io.grpc.stub.StreamObserver<DeviceOuterClass.Response> responseObserver) {
			
			//Missing DeviceOuterClass.Request object
			if(request == DeviceOuterClass.Request.getDefaultInstance()) {
				responseObserver.onError(Status.UNIMPLEMENTED.withDescription("gRPC call is incomplete.  A part of the request is missing.  Double check:\nRequest { \n\tGetDiagnosticsRequest {}\n} is being sent.").asRuntimeException());
				return;
			}
			
			Date jan_01_70 = new Date("01/01/70 00:00:00");
			Date jan_06_80 = new Date("01/06/80 00:00:00");
			long gpsEpochDiffMS = jan_06_80.getTime() - jan_01_70.getTime();
			
			//Can only send one as they are on separate servers (9200/9000) and joined together with "oneof"
			//But the purpose of this test server is primarily dish diagnostics, as I don't really use the supplied wifi capabilities.
			//WifiGetDiagnosticsResponse wifiResponse = WifiGetDiagnosticsResponse.newBuilder().setId("WifiNetwork").build();
			DishGetDiagnosticsResponse dishResponse = DishGetDiagnosticsResponse.newBuilder()
					.setId("1234567")
					.setHardwareVersion("1.2.3")
					.setSoftwareVersion("4.5.6")
					//I believe this is actually in seconds and not hours.  I received 18000 in an actual test which would be 5 hours converted to seconds.
					.setUtcOffsetS(-5)
					.setHardwareSelfTest(
							TestResult.PASSED)
					//.addHardwareSelfTestCodes(TestResultCode.CPU_VOLTAGE)
					//.addHardwareSelfTestCodes(TestResultCode.TEMPERATURE)
					.setAlerts(Alerts.newBuilder()
							.setDishThermalThrottle(true)
							.setObstructed(true))
					.setDisablementCode(DisablementCode.OKAY)
					.setLocation(Location.newBuilder()
							.setEnabled(true)
							.setLatitude(33.413791)
							.setLongitude(-82.141163)
							.setAltitudeMeters(100)
							.setUncertaintyMetersValid(true)
							.setUncertaintyMeters(10)
							.setGpsTimeS((System.currentTimeMillis() - gpsEpochDiffMS) / 1000))
					.setAlignmentStats(AlignmentStats.newBuilder()
							.setBoresightElevationDeg(39.81f)
							.setDesiredBoresightElevationDeg(39.96f)
							.setBoresightAzimuthDeg(134.21f)
							.setDesiredBoresightAzimuthDeg(134.31f))
					.setStowed(false)
					.build();
			//WifiGetDiagnosticsResponse wifiResponse = WifiGetDiagnosticsResponse.newBuilder().setId("WifiNetwork").build();
			
			DeviceOuterClass.Response response = DeviceOuterClass.Response.newBuilder().setDishGetDiagnostics(dishResponse).build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		}
	}
}

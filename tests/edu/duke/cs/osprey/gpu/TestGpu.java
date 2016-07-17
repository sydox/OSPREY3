package edu.duke.cs.osprey.gpu;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

import edu.duke.cs.osprey.gpu.kernels.TestFancyKernel;

public class TestGpu {
	
	@Test
	public void testAccuracy()
	throws Exception {
		
		// init random doubles
		final int n = 1024 * 1024;
		double[] a = new double[n];
		double[] b = new double[n];
		double[] out = new double[n];
		Random rand = new Random(12345);
		for (int i=0; i<n; i++) {
			a[i] = rand.nextDouble();
			b[i] = rand.nextDouble();
			out[i] = Math.sqrt(a[i]*a[i] + b[i]*b[i]);
		}
		
		final int NumRuns = 10;
		
		TestFancyKernel.Bound kernel = new TestFancyKernel().bind();
		
		// copy data to buffers
		kernel.setArgs(n);
		for (int i=0; i<n; i++) {
			kernel.getA().put(a[i]);
			kernel.getB().put(b[i]);
		}
		kernel.getA().rewind();
		kernel.getB().rewind();
		
		// upload args to gpu
		kernel.uploadSync();
		
		for (int i=0; i<NumRuns; i++) {
			
			// run the kernel
			kernel.runSync();
			kernel.downloadSync();
			
			// check the results for accuracy
			for (int j=0; j<n; j++) {
				double gpuVal = kernel.getOut().get(j);
				double err = Math.abs(out[j] - gpuVal);
				assertThat(err, lessThanOrEqualTo(1e-15));
			}
		}
	}
}

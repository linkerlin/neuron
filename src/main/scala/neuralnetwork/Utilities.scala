// Created by: Jianbo Ye, Penn State University jxy198@psu.edu
// Last Updated: April 2014
// Copyright under MIT License
package neuralnetwork

/********************************************************************************************/
// Numerical classes and their operations : interface to breeze
import breeze.generic._
import breeze.linalg._
import breeze.numerics._
import breeze.optimize._

import breeze.stats.distributions._
//import breeze.math._


class NeuronVector (var data: DenseVector[Double]) {
  val length = data.length
  def this(n:Int) = this(DenseVector.zeros[Double] (n))
  def this(n:Int, rand: Rand[Double]) = this(DenseVector.rand(n, rand)) // uniform sampling, might not be a good default choice
  def concatenate (that: NeuronVector) : NeuronVector = new NeuronVector(DenseVector.vertcat(this.data, that.data))
  def splice(num: Int) : (NeuronVector, NeuronVector) = (new NeuronVector(this.data(0 until num)), new NeuronVector(this.data(num to -1)))

  def -(that:NeuronVector): NeuronVector = new NeuronVector(this.data - that.data)
  def +(that:NeuronVector): NeuronVector = new NeuronVector(this.data + that.data)
  def *(x:Double) : NeuronVector = new NeuronVector(this.data * x)
  def /(x:Double) : NeuronVector = new NeuronVector(this.data / x)
  def :=(that: NeuronVector): Unit = {this.data := that.data; }
  def +=(that: NeuronVector): Unit = {this.data :+= that.data; }
  def :*=(x:Double): Unit = {this.data :*= x}
  def :/=(x:Double): Unit = {this.data :/= x}
  def euclideanSqrNorm = {val z = norm(data); z*z}
  def DOT(that: NeuronVector): NeuronVector = new NeuronVector(this.data :* that.data)
  def CROSS (that: NeuronVector): Weight = new Weight(this.data.asDenseMatrix.t * that.data.asDenseMatrix)
  
  def set(x:Double) : Unit = {data:=x; }
  def copy(): NeuronVector = new NeuronVector(data.copy)
  def sum(): Double = data.sum
  def asWeight(rows:Int, cols:Int): Weight = new Weight (data.asDenseMatrix.reshape(rows, cols)) 
  def last(): Double = data(data.length)
  def append(last: Double): NeuronVector = new NeuronVector(DenseVector.vertcat(data, DenseVector(last)) )
  //override def toString() = data.toString
}
class Weight (var data:DenseMatrix[Double]){
  def this(rows:Int, cols:Int) = this(DenseMatrix.zeros[Double](rows,cols))
  def this(rows:Int, cols:Int, rand: Rand[Double]) = this(DenseMatrix.rand(rows, cols, rand)) // will be fixed in next release
  def +(that: Weight): Weight = new Weight(this.data + that.data)
  def *(x:NeuronVector):NeuronVector = new NeuronVector(data * x.data)
  def Mult(x:NeuronVector) = this * x
  def TransMult(x:NeuronVector): NeuronVector = new NeuronVector(this.data.t * x.data)
  def *(x:Double): Weight = new Weight(this.data * x)
  def :=(that:Weight): Unit = {this.data := that.data}
  def +=(that:Weight): Unit = {this.data :+= that.data}
  def :*=(x:Double): Unit = {this.data :*= x}
  def vec(isView: Boolean = true) = new NeuronVector(data.flatten(isView))  // important!
  def transpose = new Weight(data.t)
  def set(x: Double) : Unit={data:=x; }
  def euclideanSqrNorm: Double = {val z = norm(data.flatten()); z*z}
  def sumCol() = new NeuronVector(sum(data, Axis._0).toDenseVector)
  def sumRow() = new NeuronVector(sum(data, Axis._1).toDenseVector)
}

class WeightVector (data: DenseVector[Double]) extends NeuronVector(data) {
  def this(n:Int) = this(DenseVector.zeros[Double](n))
  def this(n:Int, rand: Rand[Double]) = this(DenseVector.rand(n, rand))
  var ptr : Int = 0
  def reset(): Unit = {ptr = 0; }
  def apply(W:Weight, b:NeuronVector): Int = {
    val rows = W.data.rows
    val cols = W.data.cols
    
    W.data = data(ptr until ptr + rows*cols).asDenseMatrix.reshape(rows, cols)
    ptr = (ptr + rows * cols) % length
    b.data = data(ptr until ptr + b.length)
    ptr = (ptr + b.length) % length
    ptr
  }
  def get(W:Weight, b:NeuronVector): Int = {
    val rows = W.data.rows
    val cols = W.data.cols
    
    data(ptr until ptr + rows*cols).asDenseMatrix.reshape(rows, cols) := W.data
    ptr = (ptr + rows * cols) % length
    data(ptr until ptr + b.length) := b.data
    ptr = (ptr + b.length) % length
    ptr
  }
  def set(wv: NeuronVector): Int = {
    ptr = 0
    data := wv.data
    0
  }
  override def copy(): WeightVector = new WeightVector(data.copy)
}


object NullVector extends NeuronVector (0)
object OneVector extends NeuronVector(DenseVector(1.0)) 

object NullWeight extends Weight (0,0)

abstract class NeuronFunction {
  def grad(x:NeuronVector): NeuronVector
  def apply(x:NeuronVector): NeuronVector
}

object IndentityFunction extends NeuronFunction {
  def grad(x:NeuronVector): NeuronVector = new NeuronVector(DenseVector.ones[Double](x.data.length))
  def apply(x:NeuronVector) = x
}

object sigmoid extends UFunc with MappingUFunc{
    implicit object implDouble extends Impl [Double, Double] {
      def apply(a:Double) = 1/(1+scala.math.exp(-a))
    }
}
object dsgm extends UFunc with MappingUFunc{
    implicit object implDouble extends Impl [Double, Double] {
      def apply(a:Double) = {
        var b = scala.math.exp(-a)
        b/((1+b)*(1+b))
      }
    }
}


class KLdiv (rho:Double) extends UFunc with MappingUFunc {
  implicit object implDouble extends Impl [Double, Double] {
    def apply(a: Double) = {
      rho * log(rho/a) + (1-rho) * log((1-rho)/(1-a))
    }
  }
}
class dKLd (rho:Double) extends UFunc with MappingUFunc {
  implicit object implDouble extends Impl [Double, Double] {
    def apply(a: Double) = {
      - rho/a + (1-rho)/(1-a)
    }
  }
}
  
object SigmoidFunction extends NeuronFunction {
  def grad(x:NeuronVector): NeuronVector = new NeuronVector(dsgm(x.data)) // can be simplified, if apply() is applied first
  def apply(x:NeuronVector): NeuronVector= new NeuronVector(sigmoid(x.data))
}


class KL_divergenceFunction(val rho: Double) extends NeuronFunction {
  object dKLdfunc extends dKLd(rho)
  object KLdiv extends KLdiv(rho)
  def grad(x:NeuronVector): NeuronVector = new NeuronVector(dKLdfunc(x.data))
  def apply(x:NeuronVector): NeuronVector = new NeuronVector(KLdiv(x.data))
}

abstract class DistanceFunction {
  def grad(x:NeuronVector, y:NeuronVector): NeuronVector
  def apply(x:NeuronVector, y:NeuronVector): Double
}

object L2Distance extends DistanceFunction {
  def grad(x:NeuronVector, y:NeuronVector): NeuronVector = (x - y)
  def apply(x:NeuronVector, y:NeuronVector) = (x - y).euclideanSqrNorm /2.0
}
object SoftMaxDistance extends DistanceFunction {
  def grad(x:NeuronVector, y:NeuronVector): NeuronVector ={
    assert(abs(y.sum - 1) < 1E-6) // y must be a probability distribution
    val x1 = new NeuronVector(exp(x.data))
    (x1/x1.sum - y)
  }
  def apply(x:NeuronVector, y:NeuronVector): Double = {
    val x1 = new NeuronVector(exp(x.data))
    val x2 = new NeuronVector(-log(x1.data / x1.data.sum))
    (y DOT x2).sum
  }
}

/********************************************************************************************/
// Implement batch mode training 
abstract trait Optimizable {
  /*************************************/
  // To be specified 
  var nn: InstanceOfNeuralNetwork = null
  var xData : Array[NeuronVector] = null
  var yData : Array[NeuronVector] = null
  var xDataTest : Array[NeuronVector] = null
  var yDataTest : Array[NeuronVector] = null
  /*************************************/
  
  final var randomGenerator = new scala.util.Random
  
  def initMemory(inn: InstanceOfNeuralNetwork = nn) : SetOfMemorables = {
    val seed = ((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString
    val mem = new SetOfMemorables
    inn.init(seed, mem).allocate(seed, mem)
    mem
  }
  
  def getRandomWeightVector () : WeightVector = {
    
    val wdefault = nn.getRandomWeights(System.currentTimeMillis().hashCode.toString) // get dimension of weights
    val rv = new WeightVector(wdefault.length) 
    rv := wdefault
    rv
  }
  
  def getObj(w: WeightVector, distance:DistanceFunction = L2Distance) : Double = { // doesnot compute gradient or backpropagation
    val size = xData.length
    assert (size >= 1 && size == yData.length)
    var totalCost: Double = 0.0
    val dw = new WeightVector(w.length)
    
    
    nn.setWeights(((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString, w)    
    (0 until size).par.foreach(i => {
      nn(xData(i),initMemory())
    })
    
    
    nn.setWeights(((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString, w)
    totalCost = (0 until size).par.map(i => {
      distance(nn(xData(i), initMemory()), yData(i))
    }).reduce(_+_)
    
    val regCost = nn.getDerativeOfWeights(((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString, dw, size)
    totalCost/size + regCost
  }
  
  def getObjAndGrad (w: WeightVector, distance:DistanceFunction = L2Distance): (Double, NeuronVector) = {
    val size = xData.length
    assert(size >= 1 && size == yData.length)
    var totalCost:Double = 0.0
    /*
     * Compute objective and gradients in batch mode
     * which can be run in parallel 
     */
    
    val dw = new WeightVector(w.length)
    nn.setWeights(((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString, w)
    
    
    (0 until size).par.foreach(i => {
      nn(xData(i),initMemory())
    })
    
    
    
    nn.setWeights(((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString, w)
    totalCost = (0 until size).par.map(i => {
      val mem = initMemory()
      val x = nn(xData(i), mem); val y = yData(i)
      val z = distance.grad(x, yData(i))
      nn.backpropagate(z, mem) // update dw !
      distance(x,y)
    }).reduce(_+_)
    /*
     * End parallel loop
     */
    
    
    val regCost = nn.getDerativeOfWeights(((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString, dw, size)
    //println(totalCost/size, regCost)
    (totalCost/size + regCost, dw/size)
  }
  
  def getApproximateObjAndGrad (w: WeightVector, distance:DistanceFunction = L2Distance) : (Double, NeuronVector) = {
    // Compute gradient using numerical approximation
    var dW = w.copy()
    for (i<- 0 until w.length) {
	  val epsilon = 0.00001
	  val w2 = w.copy
	  w2.data(i) = w.data(i) + epsilon
	  val cost1 = getObj(w2, distance)
	  w2.data(i) = w.data(i) - epsilon
	  val cost2 = getObj(w2, distance)
	  
	  dW.data(i) = (cost1 - cost2) / (2*epsilon)
	}
    (getObj(w, distance), dW)
  }
  

  /*
   * Train neural network using first order minimizer (L-BFGS)
   * Please NOTE there is no regularization penalty in training 
   * Regularization usually is done in distributed modules
   */ 
  def train(w: WeightVector, maxIter:Int = 400, distance: DistanceFunction = L2Distance): (Double, WeightVector) = {

    val f = new DiffFunction[DenseVector[Double]] {
	  def calculate(x: DenseVector[Double]) = {
	    val (obj, grad) = getObjAndGrad(new WeightVector(x), distance)
	    (obj, grad.data)
	  }    
    }
    
    val lbfgs = new LBFGS[DenseVector[Double]](maxIter)
    val w2 = new WeightVector(lbfgs.minimize(f, w.data))
    
    //val sgd =  StochasticGradientDescent[DenseVector[Double]](1.0,maxIter)    
    //val w2 = new WeightVector(sgd.minimize(f, w.data))
    
    (f(w2.data), w2)
  }
  
  def test(w:WeightVector, distance: DistanceFunction = L2Distance): Double = {
    val size = xDataTest.length
    nn.setWeights(((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString, w)
    val totalCost = (0 until size).par.map(i => {
      		distance(nn(xDataTest(i), initMemory()), yDataTest(i))
      	}).reduce(_+_)
    totalCost / size
  }
}
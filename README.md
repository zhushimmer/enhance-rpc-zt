# description
一款自研RPC框架，通过对原始Dubbo的使用，并进行测试，发现一些性能问题，之后做出的修改与性能提升，本身一款软件不可能是完美无缺，对于软件的自研提升性能的同时，对底层框架的了解，有助于自己更好，更高效的使用
# Usage and Enhance
## 1 Future change CompletaleFutue
Future:表示异步计算结果的接口

  我有一个任务，提交给了 Future 来处理。任务执行期间我自己可以去做任何想做的事情。并且，在这期间我还可以取消任务以及获取任务的执行状态。一段时间之后，我就可以 Future 那里直接取出任务执行结果。
  
  Future 在实际使用过程中存在一些局限性比如不支持异步任务的编排组合、获取计算结果的 get() 方法为阻塞调用。
  
CompletaleFutue: 基于事件驱动的异步回调类

  CompletableFuture 除了提供了更为好用和强大的 Future 特性之外，还提供了函数式编程、异步任务编排组合（可以将多个异步任务串联起来，组成一个完整的链式调用）等能力。


### 问题发现：


如果一个方法里面涉及到了 3 个 rpc 调用，假设每个 rpc 调用都需要 10ms，那么这个方法总耗时将不低于 30ms。

```JAVA
public boolean provingOrder(long userId, long itemId, double rate) {
    
    // 验证用户能否享受这一待遇，RPC调用
    boolean provingDiscount = provingService.proving(userId, itemId, rate);
    
    if(!provingDiscount) {
        // 该用户无法享受这一折扣
        return false;
    }
    
    // 获取药品单价，RPC调用
    double itemPrice = storeService.getPrice(itemId);
    
    // 用户实际应该支付的药品价格
    double realPrice = itemPrice * rate;
    
    // 获取用户账号历年余额，限定了只能使用历年余额购买，RPC调用
    double balance = userService.getBalance(userId);
            
    return realPrice <= balance;
}
```

在同步调用系统中，延迟同时会导致吞吐量的下降。如果只有一个线程，那么系统每秒的吞吐量将不会高于 1000ms / 30ms，也就是最多 33 qps。同步系统要提高吞吐量，唯一的办法就是加大线程数。同时启用 1,000 个线程，吞吐量理论值可以上升到 33,333 qps。不过实际使用中，这并不是完美的方案：增加线程数量会导致频繁的上下文切换，系统整体性能将会严重下降。


引入Future：3 个 rpc 调用可以同时进行了，系统延迟降低为之前的 1/3。不过延迟降低吞吐量的问题还是没有解决，依然需要通过增加线程数来提升吞吐量。
```Java
public boolean provingOrder(long userId, long itemId, double rate) {
  // 验证用户能否享受这一待遇，RPC调用
    Future<Boolean> provingDiscountFuture = discountService.proving(userId, itemId, rate);
    
   // 获取药品单价，RPC调用
    Future<Double> itemPriceFuture = storeService.getPrice(itemId);
    
 // 获取用户账号历年余额，限定了只能使用历年余额购买，RPC调用
    Future<Double> balanceFuture = userService.getBalance(userId);

    if(!provingDiscountFuture.get()) {
        // 该用户无法享受这一折扣
        return false;
    }

    // 用户实际应该支付的药品价格
    double realPrice = itemPriceFuture.get() * rate;

    // 用户账号历年余额
    double balance = balanceFuture.get();
            
    return realPrice <= balance;
}
```

引入CompletableFuture:延迟降低为原来 1/3，同时吞吐量也不会因为延迟而降低。非常完美，简单高效，CompletableFuture 绝对称得上是大杀器。在 rpc 异步调用这个问题上，没什么比 CompletableFuture 更适合的解决方案了。CompletableFuture 是 Doug Lea 的又一力作，彻底解决了 Future 的缺陷，把 Java 带入了异步响应式编程的新世界。
```Java
 
public boolean provingOrder(long userId, long itemId, double rate) {
  // 验证用户能否享受这一待遇，RPC调用
    Future<Boolean> provingDiscountFuture = discountService.proving(userId, itemId, rate);
    
   // 获取药品单价，RPC调用
    Future<Double> itemPriceFuture = storeService.getPrice(itemId);
    
 // 获取用户账号历年余额，限定了只能使用历年余额购买，RPC调用
    Future<Double> balanceFuture = userService.getBalance(userId);

return CompletableFuture
        .allOf(verifyDiscountFuture, itemPriceFuture, balanceFuture)
        .thenApply(v -> {
            if(!verifyDiscountFuture.get()) {
                // 该用户无法享受这一折扣
                return false;
            }

            // 用户实际应该支付的价格
            double realPrice = itemPriceFuture.get() * rate;

            // 用户账号余额
            double balance = balanceFuture.get();
                    
            return realPrice <= balance;
        });    
}
```

  demo1:列出一个小demo,具体部分见代码
  ```Java
private <T> CompletableFuture<T> handleResult(//
			final Request request, //
			final CompletableFuture<Response> future, //
			final Invoker<CompletableFuture<?>> failoverInvoker, //
			final MethodParam methodParam) {
```
## 2 Serializable and Deserialize  pull_in MethodParam

高性能的方法参数封装，减少自动装箱调用，通过字节码生成直接调用。

方法参数封装，用于序列化传输参数数据，其实现类会自动根据方法名称生成get/set方法。

```Java
public static Class<? extends MethodParam> createClass(Method method)
			throws CannotCompileException, NotFoundException {
		Objects.requireNonNull(method, "method must not be null");

		if (method.getParameterCount() == 0) {
			return EmptyMethodParam.class;
		}

		Class<? extends MethodParam> methodParamClass = methodParamClassMap.get(method);
		if (methodParamClass != null) {
			return methodParamClass;
		}

		synchronized (MethodParamClassFactory.class) {
			methodParamClass = methodParamClassMap.get(method);
			if (methodParamClass != null) {
				return methodParamClass;
			}

			methodParamClass = doCreateClass(method);
			methodParamClassMap.put(method, methodParamClass);
		}

		return methodParamClass;
	}
```
## 3
Dubbo 的消息格式
```Java
public class RpcInvocation implements Invocation, Serializable {
    private String methodName;
    private Class<?>[] parameterTypes;
    private Object[] arguments;
    ...
}
```
基本的内置服务，建立连接后需要调用,将方法以及参数封装成MethodParam类，固定死顺序，保证serviceId为预设值，为每个方法设置一个id存入ConcurrentHashMap.通过服务id获取invoker
```Java
	@Override
	public CompletableFuture<List<String>> getClassRegisterList() {
		return CompletableFuture.completedFuture(invokerFactory.getClassRegisterList());
	}
```

# Contact
If you have any issues or feature requests, please contact us. PR is welcomed.
https://github.com/zhushimmer/enhance-rpc-zt/issues

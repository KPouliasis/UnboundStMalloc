import sun.java2d.pipe.SpanShapeRenderer;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * Created by kpoul on 4/6/14.
 */


    /**
     * Created by existentialtype on 4/6/14.
     */
    public class UnboundedStackMalloc <T> {


        ThreadLocal <Set <Node<T>>> nodes_pool =
                new ThreadLocal <Set <Node<T>>>  () {
                    @Override
                    protected Set<Node<T>> initialValue() {
                        ArrayList<Node<T>> init=new ArrayList<Node<T>>(100);
                        //i can push up to 100 objects without recycling
                        for (int i = 0; i < 100; i++) {
                            init.add(null);
                        }
                        return new HashSet<Node<T>>(init);
                    }
                };

        AtomicStampedReference<Node> top = new AtomicStampedReference<Node>(null,0);



        //static final int MIN_DELAY = ...;
        //static final int MAX_DELAY = ...;
        // Backoff backoff = new Backoff(MIN_DELAY, MAX_DELAY);
        protected boolean tryPush(T data) {
            if (nodes_pool.get().iterator().hasNext()) {
                Node<T> newNode = nodes_pool.get().iterator().next();
                newNode.value=data;
                Node<T> oldTop = top.getReference();
                newNode.next = oldTop;
                //hack to trivialize comparison on timestamps since pushing does not
                // casue ABA issues
                return (top.compareAndSet(oldTop, newNode,1,1));
            }
            else {return false;}
        }
        public void push(T value) {
            while (true) {
                if (tryPush(value)) {
                    return;
                } else {
                    backoff.backoff();
                }
            }
        }

        public class EmptyException extends Exception {
        }
        //modified trypop to recycle local pool thread objects
        //and avoid the ABA issue
        protected Node <T> tryPop() throws EmptyException {
            int[] topStamp=new int[1];


            Node<T> oldTop = top.get(topStamp);

            if (oldTop == null) {
                throw new EmptyException();
            }
            Node newTop = oldTop.next;
            if (top.compareAndSet(oldTop, newTop,topStamp[0],topStamp[0]+1)) {

                nodes_pool.get().add(oldTop);

                return oldTop;
            }else {
                return null;
            }
        }

        public T pop() throws EmptyException {
            while (true) {
                Node <T> returnNode = tryPop();
                if (returnNode != null) {
                    return returnNode.value;
                } else {
                    backoff.backoff();
                }
            }
        }

    }



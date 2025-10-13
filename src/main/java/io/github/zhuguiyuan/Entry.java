package io.github.zhuguiyuan;

import java.util.HashMap;
import java.util.LinkedList;
import static io.github.zhuguiyuan.MFun.*;

class MFun {
  sealed interface Exp {
  }

  record Int(Integer v) implements Exp {
  }

  record Var(String id) implements Exp {
  }

  record Add(Exp left, Exp right) implements Exp {
  }

  record Fun(Var var, Exp body) implements Exp {
  }

  record App(Exp fun, Exp arg) implements Exp {
  }

  // examples
  static Exp add2 = new Fun(new Var("x"), new Add(new Var("x"), new Int(2)));
  static Exp apply2 = new Fun(new Var("f"), new Fun(new Var("y"),
      new App(new Var("f"), new App(new Var("f"), new Var("y")))));
  static Exp add4 = new App(apply2, add2);
  static Exp program42 = new App(add4, new Int(38));
}

class MEvalSubst {
  sealed interface Value {
  }

  record IntV(Integer v) implements Value {
  }

  record FunV(Var var, Exp body) implements Value {
  }

  static Exp exp_of_value(Value v) {
    return switch (v) {
      case IntV(var i) -> new Int(i);
      case FunV(var arg, var body) -> new Fun(arg, body);
    };
  }

  static Exp subst(Value substVal, Var substVar, Exp exp) {
    return switch (exp) {
      case Int i -> i;
      case Var var -> var.equals(substVar) ? exp_of_value(substVal) : exp;
      case Add(var left, var right) -> new Add(subst(substVal, substVar, left),
          subst(substVal, substVar, right));
      case Fun(var arg, var body) -> arg.equals(substVar) ? exp
          : new Fun(arg, subst(substVal, substVar, body));
      case App(var fun, var arg) -> new App(subst(substVal, substVar, fun),
          subst(substVal, substVar, arg));
    };
  }

  static Value eval(Exp exp) {
    return switch (exp) {
      case Int(var i) -> new IntV(i);
      case Var var -> throw new RuntimeException("unbound variable: " + var);
      case Add(var left, var right) -> {
        var lv = eval(left);
        var rv = eval(right);
        if (lv instanceof IntV(var li) && rv instanceof IntV(var ri)) {
          yield new IntV(li + ri);
        } else {
          throw new RuntimeException("type error in addition");
        }
      }
      case Fun(var arg, var body) -> new FunV(arg, body);
      case App(var exp1, var exp2) -> {
        var fv = eval(exp1);
        var av = eval(exp2);
        if (fv instanceof FunV(var arg, var body)) {
          yield eval(subst(av, arg, body));
        } else {
          throw new RuntimeException("type error in application");
        }
      }
    };
  }

  public static void main(String[] args) {
    var result = MEvalSubst.eval(MFun.program42);
    System.out.println(result);
  }
}

class MEvalEnv {
  sealed interface Value {
  }

  record IntV(Integer v) implements Value {
  }

  record ClosureV(Env env, Var var, Exp body) implements Value {
  }

  record EnvEntity(Var var, Value value) {
  }

  static class Env extends LinkedList<EnvEntity> {
    public void extend(Var var, Value value) {
      this.addFirst(new EnvEntity(var, value));
    }

    public Value lookup(Var var) {
      for (EnvEntity entity : this) {
        if (entity.var().equals(var)) {
          return entity.value();
        }
      }
      throw new RuntimeException("unbound variable: " + var);
    }
  }

  static Value eval(Env env, Exp exp) {
    return switch (exp) {
      case Int(var i) -> new IntV(i);
      case Var var -> env.lookup(var);
      case Add(var left, var right) -> {
        var lv = eval(env, left);
        var rv = eval(env, right);
        if (lv instanceof IntV(var li) && rv instanceof IntV(var ri)) {
          yield new IntV(li + ri);
        } else {
          throw new RuntimeException("type error in addition");
        }
      }
      case Fun(var arg, var body) -> new ClosureV(env, arg, body);
      case App(var exp1, var exp2) -> {
        var fv = eval(env, exp1);
        var av = eval(env, exp2);
        if (fv instanceof ClosureV(var closureEnv, var arg, var body)) {
          var newEnv = new Env();
          newEnv.addAll(closureEnv);
          newEnv.extend(arg, av);
          yield eval(newEnv, body);
        } else {
          throw new RuntimeException("type error in application");
        }
      }
    };
  }

  static void main(String[] args) {
    var result = MEvalEnv.eval(new Env(), MFun.program42);
    System.out.println(result);
  }
}

class MObjectEnvIR {
  sealed interface Exp {
  }

  record Val(Value v) implements Exp {
  }

  record Var(String id) implements Exp {
  }

  record Global(String id) implements Exp {
  }

  record Add(Exp left, Exp right) implements Exp {
  }

  record App(Exp fun, Exp arg) implements Exp {
  }

  record Tuple(Exp... exps) implements Exp {
    @Override
    public String toString() {
      var sb = new StringBuilder();
      sb.append("Tuple[");
      for (int i = 0; i < exps.length; i++) {
        sb.append(exps[i]);
        if (i != exps.length - 1) {
          sb.append(", ");
        }
      }
      sb.append("]");
      return sb.toString();
    }
  }

  record Proj(Exp exp, Integer index) implements Exp {
  }

  sealed interface Value {
  }

  record IntV(Integer v) implements Value {
  }

  record ClosureV(Var env, Var var, Exp body) implements Value {
  }

  record TupleV(Value... vs) implements Value {
  }

  static class Env extends LinkedList<Var> {
  }

  static int uniqNameCnt = 0;

  static String mkUniqName(String hint) {
    return "%" + hint + "." + (uniqNameCnt++);
  }

  static Tuple buildCloureEnv(Env env) {
    return new Tuple(env.toArray(new Var[0]));
  }

  static Exp buildLocalEnv(Env env, Var envName, Exp body) {
    return switch (body) {
      case Val v -> v;
      case Var var -> {
        var index = env.indexOf(var);
        if (index != -1) {
          yield new Proj(envName, index);
        } else {
          yield var;
        }
      }
      case Global g -> g;
      case Add(var left, var right) -> new Add(
          buildLocalEnv(env, envName, left),
          buildLocalEnv(env, envName, right));
      case App(var fun, var arg) -> new App(buildLocalEnv(env, envName, fun),
          buildLocalEnv(env, envName, arg));
      case Tuple exps -> {
        var newExps = new Exp[exps.exps().length];
        for (int i = 0; i < exps.exps().length; i++) {
          newExps[i] = buildLocalEnv(env, envName, exps.exps()[i]);
        }
        yield new Tuple(newExps);
      }
      case Proj(var tuple, var index) -> new Proj(
          buildLocalEnv(env, envName, tuple), index);
    };
  }

  static Exp convert(Env env, MFun.Exp exp) {
    return switch (exp) {
      case MFun.Int(var i) -> new Val(new IntV(i));
      case MFun.Var(var id) -> new Var(id);
      case MFun.Add(var left, var right) -> new Add(convert(env, left),
          convert(env, right));
      case MFun.Fun(var arg, var body) -> {
        var envName = new Var(mkUniqName("env"));
        var closureEnvTuple = buildCloureEnv(env);
        var irArg = new Var(arg.id());
        var newEnv = new Env();
        newEnv.addAll(env);
        newEnv.addFirst(irArg);
        var bodyConv = buildLocalEnv(env, envName, convert(newEnv, body));
        yield new Tuple(closureEnvTuple,
            new Val(new ClosureV(envName, irArg, bodyConv)));
      }
      case MFun.App(var fun, var arg) -> new App(convert(env, fun),
          convert(env, arg));
    };
  }

  static class GlobalMapWith<T> extends HashMap<Global, Value> {
    T top;

    @Override
    public String toString() {
      var sb = new StringBuilder();
      for (var item : keySet()) {
        sb.append(item).append(" => ").append(this.get(item)).append("\n");
      }
      sb.append(top);
      return sb.toString();
    }
  }

  static GlobalMapWith<Exp> hoist_exp(Exp exp) {
    return switch (exp) {
      case Val(ClosureV(var env, var arg, var body)) -> {
        var bodyMap = hoist_exp(body);
        var map = new GlobalMapWith<Exp>();
        map.putAll(bodyMap);
        var gName = new Global(mkUniqName("CODE"));
        map.put(gName, new ClosureV(env, arg, bodyMap.top));
        map.top = gName;
        yield map;
      }
      case Val(var v) -> {
        var valueMap = hoist_value(v);
        var map = new GlobalMapWith<Exp>();
        map.putAll(valueMap);
        map.top = new Val(valueMap.top);
        yield map;
      }
      case Var var -> {
        var map = new GlobalMapWith<Exp>();
        map.top = var;
        yield map;
      }
      case Global g -> {
        var map = new GlobalMapWith<Exp>();
        map.top = g;
        yield map;
      }
      case Add(var left, var right) -> {
        var leftMap = hoist_exp(left);
        var rightMap = hoist_exp(right);
        var map = new GlobalMapWith<Exp>();
        map.putAll(leftMap);
        map.putAll(rightMap);
        map.top = new Add(leftMap.top, rightMap.top);
        yield map;
      }
      case App(var fun, var arg) -> {
        var funMap = hoist_exp(fun);
        var argMap = hoist_exp(arg);
        var map = new GlobalMapWith<Exp>();
        map.putAll(funMap);
        map.putAll(argMap);
        map.top = new App(funMap.top, argMap.top);
        yield map;
      }
      case Tuple(var exps) -> {
        var map = new GlobalMapWith<Exp>();
        var newExps = new Exp[exps.length];
        for (int i = 0; i < exps.length; i++) {
          var expMap = hoist_exp(exps[i]);
          map.putAll(expMap);
          newExps[i] = expMap.top;
        }
        map.top = new Tuple(newExps);
        yield map;
      }
      case Proj(var tuple, var index) -> {
        var tupleMap = hoist_exp(tuple);
        var map = new GlobalMapWith<Exp>();
        map.putAll(tupleMap);
        map.top = new Proj(tupleMap.top, index);
        yield map;
      }
    };
  }

  static GlobalMapWith<Value> hoist_value(Value v) {
    return switch (v) {
      case IntV i -> {
        var map = new GlobalMapWith<Value>();
        map.top = i;
        yield map;
      }
      case TupleV(var vs) -> {
        var map = new GlobalMapWith<Value>();
        var newVs = new Value[vs.length];
        for (int i = 0; i < vs.length; i++) {
          var vMap = hoist_value(vs[i]);
          map.putAll(vMap);
          newVs[i] = vMap.top;
        }
        map.top = new TupleV(newVs);
        yield map;
      }
      default -> {
        throw new RuntimeException("cannot hoist function value");
      }
    };
  }

  static Exp subst(Exp substExp, Var substVar, Exp exp) {
    return switch (exp) {
      case Val v -> v;
      case Var var -> var.equals(substVar) ? substExp : var;
      case Global g -> g;
      case Add(var left, var right) -> new Add(subst(substExp, substVar, left),
          subst(substExp, substVar, right));
      case App(var fun, var arg) -> new App(subst(substExp, substVar, fun),
          subst(substExp, substVar, arg));
      case Tuple exps -> {
        var newExps = new Exp[exps.exps().length];
        for (int i = 0; i < exps.exps().length; i++) {
          newExps[i] = subst(substExp, substVar, exps.exps()[i]);
        }
        yield new Tuple(newExps);
      }
      case Proj(var tuple, var index) -> new Proj(
          subst(substExp, substVar, tuple), index);
    };
  }

  static Value eval(GlobalMapWith<Exp> program) {
    return evalHelper(program, null);
  }

  private static Value evalHelper(GlobalMapWith<Exp> program, Exp currExp) {
    if (currExp == null) {
      currExp = program.top;
    }
    return switch (currExp) {
      case Var var -> throw new RuntimeException("unbound variable: " + var);
      case Global var -> program.get(var);
      case Val(var v) -> v;
      case Add(var left, var right) -> {
        var lv = evalHelper(program, left);
        var rv = evalHelper(program, right);
        if (lv instanceof IntV(var li) && rv instanceof IntV(var ri)) {
          yield new IntV(li + ri);
        } else {
          throw new RuntimeException("type error in addition");
        }
      }
      case App(var fun, var arg) -> {
        var fv = evalHelper(program, fun);
        var av = evalHelper(program, arg);
        if (fv instanceof TupleV(var tupleVs)
            && tupleVs[1] instanceof ClosureV(var env, var argVar, var body)) {
          var bodySubst = subst(new Val(av), argVar, body);
          var bodySubstEnv = subst(new Val(tupleVs[0]), env, bodySubst);
          yield evalHelper(program, bodySubstEnv);
        } else {
          throw new RuntimeException("type error in application");
        }
      }
      case Tuple(var exps) -> {
        var vs = new Value[exps.length];
        for (int i = 0; i < exps.length; i++) {
          vs[i] = evalHelper(program, exps[i]);
        }
        yield new TupleV(vs);
      }
      case Proj(var tuple, var index) -> {
        var tv = evalHelper(program, tuple);
        if (tv instanceof TupleV(var vs)) {
          yield vs[index];
        } else {
          throw new RuntimeException("type error in projection");
        }
      }
    };
  }

  static void main(String[] args) {
    var converted = convert(new Env(), MFun.program42);
    var program = hoist_exp(converted);
    System.out.println(eval(program));
  }
}

public class Entry {
  public static void main(String[] args) {
    MEvalSubst.main(args);
    MEvalEnv.main(args);
    MObjectEnvIR.main(args);
  }
}

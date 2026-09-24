"""Read block-state definitions straight from mod bytecode (no game launch).

A tiny Java class-file reader plus a symbolic interpreter good enough for the idioms mods use to
declare block states on NeoForge 1.21.1 (Mojang names at runtime):

    static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b); b.add(FACING, PART, WATERLOGGED); }
    Ctor(...) { super(...); registerDefaultState(stateDefinition.any().setValue(FACING, NORTH)...); }

`Classes.state_definition(cls)` returns the ordered properties (name -> possible values) and the
default state, following superclasses into the vanilla server jar. Anything the interpreter cannot
follow is reported, never guessed silently.
"""
import struct
import zipfile

OPLEN = [1] * 256
for _op in (0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc):
    OPLEN[_op] = 2
for _op in (0x11, 0x13, 0x14, 0x84, 0xa7, 0xa8, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xc0, 0xc1,
            0xc6, 0xc7) + tuple(range(0x99, 0xa7)):
    OPLEN[_op] = 3
for _op in (0xb9, 0xba, 0xc8, 0xc9):
    OPLEN[_op] = 5
OPLEN[0xc5] = 4
CPREF = {0x13, 0x14, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xbb, 0xbd, 0xc0, 0xc1, 0xc5}

BLOCK = 'net/minecraft/world/level/block/Block'
DIRECTIONS = ['down', 'up', 'north', 'south', 'west', 'east']      # Direction.values() order
HORIZONTAL = ['north', 'south', 'west', 'east']


class ClassFile:
    def __init__(self, data):
        self.d = data
        p = 8
        n = struct.unpack_from('>H', data, p)[0]
        p += 2
        cp = [None] * n
        i = 1
        while i < n:
            t = data[p]
            p += 1
            if t == 1:
                ln = struct.unpack_from('>H', data, p)[0]
                cp[i] = ('utf8', data[p + 2:p + 2 + ln].decode('utf-8', 'replace'))
                p += 2 + ln
            elif t in (3, 4):
                cp[i] = ('int' if t == 3 else 'float', struct.unpack_from('>i', data, p)[0])
                p += 4
            elif t in (5, 6):
                cp[i] = ('long', 0)
                p += 8
                i += 1
            elif t in (7, 8, 16, 19, 20):
                cp[i] = ({7: 'class', 8: 'string', 16: 'mtype', 19: 'module', 20: 'package'}[t],
                         struct.unpack_from('>H', data, p)[0])
                p += 2
            elif t in (9, 10, 11, 12, 17, 18):
                a, b = struct.unpack_from('>HH', data, p)
                cp[i] = ({9: 'field', 10: 'method', 11: 'imethod', 12: 'nat', 17: 'dyn', 18: 'indy'}[t], a, b)
                p += 4
            elif t == 15:
                k, r = struct.unpack_from('>BH', data, p)
                cp[i] = ('mhandle', k, r)
                p += 3
            else:
                raise ValueError('constant pool tag %d' % t)
            i += 1
        self.cp = cp
        self.access, this, sup = struct.unpack_from('>HHH', data, p)
        p += 6
        self.name = self.cls(this)
        self.super = self.cls(sup) if sup else None
        ni = struct.unpack_from('>H', data, p)[0]
        self.interfaces = [self.cls(struct.unpack_from('>H', data, p + 2 + 2 * k)[0]) for k in range(ni)]
        p += 2 + 2 * ni
        self.fields, p = self._members(p)
        self.methods, p = self._members(p)
        self.attrs, p = self._attrs(p)
        self.bootstrap = []
        b = self.attrs.get('BootstrapMethods')
        if b:
            q = 2
            for _ in range(struct.unpack_from('>H', b, 0)[0]):
                ref, na = struct.unpack_from('>HH', b, q)
                self.bootstrap.append((ref, [struct.unpack_from('>H', b, q + 4 + 2 * k)[0] for k in range(na)]))
                q += 4 + 2 * na

    def utf(self, i):
        return self.cp[i][1]

    def cls(self, i):
        return self.utf(self.cp[i][1])

    def ref(self, i):
        e = self.cp[i]
        nat = self.cp[e[2]]
        return self.cls(e[1]), self.utf(nat[1]), self.utf(nat[2])

    def _attrs(self, p):
        n = struct.unpack_from('>H', self.d, p)[0]
        p += 2
        out = {}
        for _ in range(n):
            ni, ln = struct.unpack_from('>HI', self.d, p)
            out[self.utf(ni)] = self.d[p + 6:p + 6 + ln]
            p += 6 + ln
        return out, p

    def _members(self, p):
        n = struct.unpack_from('>H', self.d, p)[0]
        p += 2
        out = []
        for _ in range(n):
            acc, ni, di = struct.unpack_from('>HHH', self.d, p)
            attrs, p = self._attrs(p + 6)
            out.append({'access': acc, 'name': self.utf(ni), 'desc': self.utf(di), 'attrs': attrs})
        return out, p

    def methods_named(self, name):
        return [m for m in self.methods if m['name'] == name]

    def insns(self, m):
        c = m['attrs'].get('Code')
        if not c:
            return
        ln = struct.unpack_from('>I', c, 4)[0]
        code = c[8:8 + ln]
        pc = 0
        while pc < len(code):
            op = code[pc]
            if op == 0xaa:
                q = (pc + 4) & ~3
                lo, hi = struct.unpack_from('>ii', code, q + 4)
                yield pc, op, None
                pc = q + 12 + 4 * (hi - lo + 1)
                continue
            if op == 0xab:
                q = (pc + 4) & ~3
                yield pc, op, None
                pc = q + 8 + 8 * struct.unpack_from('>i', code, q + 4)[0]
                continue
            if op == 0xc4:
                yield pc, op, code[pc + 1]
                pc += 6 if code[pc + 1] == 0x84 else 4
                continue
            arg = None
            if op == 0x12:
                arg = code[pc + 1]
            elif op in CPREF:
                arg = struct.unpack_from('>H', code, pc + 1)[0]
            elif op in (0x10, 0xbc):
                arg = struct.unpack_from('>b', code, pc + 1)[0]
            elif op == 0x11:
                arg = struct.unpack_from('>h', code, pc + 1)[0]
            elif op in (0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a):
                arg = code[pc + 1]
            yield pc, op, arg
            pc += OPLEN[op]

    def const(self, i):
        e = self.cp[i]
        if e[0] == 'string':
            return ('str', self.utf(e[1]))
        if e[0] == 'int':
            return ('int', e[1])
        if e[0] == 'class':
            return ('class', self.utf(e[1]))
        return ('unk',)

    def indy_target(self, idx):
        """Implementation handle (kind, owner, name, desc) of a lambda / method reference."""
        bsm, args = self.bootstrap[self.cp[idx][1]]
        for a in args:
            ae = self.cp[a]
            if ae[0] == 'mhandle':
                return (ae[1],) + self.ref(ae[2])
        return None


def _nargs(desc):
    """Number of argument slots (values on the stack) in a method descriptor."""
    args = desc[1:desc.index(')')]
    n, i = 0, 0
    while i < len(args):
        c = args[i]
        if c == 'L':
            i = args.index(';', i) + 1
        elif c == '[':
            while args[i] == '[':
                i += 1
            i = args.index(';', i) + 1 if args[i] == 'L' else i + 1
        else:
            i += 1
        n += 1
    return n


def _returns(desc):
    return desc[desc.index(')') + 1:] != 'V'


class Classes:
    """Class lookup across an ordered list of JARs (the mod first, then its libraries, then vanilla)."""

    def __init__(self, jars):
        self.zips = []
        for j in jars:
            try:
                self.zips.append(zipfile.ZipFile(j))
            except (OSError, zipfile.BadZipFile):
                pass
        self.cache = {}
        self.enum_cache = {}
        self.prop_cache = {}
        self.def_cache = {}
        self.notes = []

    def get(self, name):
        if name in self.cache:
            return self.cache[name]
        cf = None
        for z in self.zips:
            try:
                cf = ClassFile(z.read(name + '.class'))
                break
            except KeyError:
                continue
        self.cache[name] = cf
        return cf

    def supers(self, name):
        out = []
        while name:
            out.append(name)
            cf = self.get(name)
            name = cf.super if cf else None
        return out

    def ancestors(self, name):
        """The class, its superclasses and every interface they implement (breadth first)."""
        out, queue = [], [name]
        while queue:
            n = queue.pop(0)
            if not n or n in out:
                continue
            out.append(n)
            cf = self.get(n)
            if cf:
                queue.extend([cf.super] + cf.interfaces)
        return out

    def is_block(self, name):
        return BLOCK in self.supers(name)

    # ---------------- symbolic execution ----------------
    def run(self, cf, m, on_call=None, locals_=None):
        """Symbolically execute `m`, returning the trace of calls and static stores. `on_call(owner,
        name, desc, args, stack)` may return a value to push."""
        st = []
        loc = dict(locals_ or {})
        stores = []

        def pop(n=1):
            out = []
            for _ in range(n):
                out.append(st.pop() if st else ('unk',))
            return out[::-1]

        for pc, op, arg in cf.insns(m):
            if op in (0x12, 0x13):
                st.append(cf.const(arg))
            elif 0x02 <= op <= 0x08:
                st.append(('int', op - 3))
            elif op in (0x10, 0x11):
                st.append(('int', arg))
            elif op == 0x01:
                st.append(('null',))
            elif op in (0x2a, 0x2b, 0x2c, 0x2d):
                st.append(loc.get(op - 0x2a, ('unk',)))
            elif op in (0x19, 0x15):
                st.append(loc.get(arg, ('unk',)))
            elif 0x1a <= op <= 0x1d:
                st.append(loc.get(op - 0x1a, ('unk',)))
            elif op in (0x4b, 0x4c, 0x4d, 0x4e):
                loc[op - 0x4b] = pop()[0]
            elif op in (0x3b, 0x3c, 0x3d, 0x3e):
                loc[op - 0x3b] = pop()[0]
            elif op in (0x3a, 0x36):
                loc[arg] = pop()[0]
            elif op == 0x59:
                st.append(st[-1] if st else ('unk',))
            elif op == 0x57:
                pop()
            elif op == 0x5a:                              # dup_x1
                a, b = pop(2)
                st.extend([b, a, b])
            elif op == 0xbb:
                st.append(('new', cf.cls(arg)))
            elif op in (0xbd, 0xbc):
                n = pop()[0]
                st.append(('arr', [None] * (n[1] if n[0] == 'int' and 0 <= n[1] < 64 else 0)))
            elif op == 0x53:                              # aastore
                arr, idx, val = pop(3)
                if arr[0] == 'arr' and idx[0] == 'int' and idx[1] < len(arr[1]):
                    arr[1][idx[1]] = val
            elif op == 0xb2:
                owner, name, desc = cf.ref(arg)
                st.append(('field', owner, name, desc))
            elif op == 0xb3:
                owner, name, desc = cf.ref(arg)
                stores.append((owner, name, pop()[0]))
            elif op == 0xb4:
                owner, name, desc = cf.ref(arg)
                obj = pop()[0]
                st.append(('getfield', owner, name, obj))
            elif op == 0xb5:
                pop(2)
            elif op == 0xc0:
                pass
            elif op in (0xb6, 0xb7, 0xb8, 0xb9):
                owner, name, desc = cf.ref(arg)
                args = pop(_nargs(desc))
                recv = pop()[0] if op != 0xb8 else None
                res = on_call(op, owner, name, desc, recv, args) if on_call else None
                if _returns(desc):
                    st.append(res if res is not None else ('call', owner, name, tuple(args), recv))
            elif op == 0xba:
                t = cf.indy_target(arg)
                cp = cf.cp[arg]
                nat = cf.cp[cp[2]]
                d = cf.utf(nat[2])
                cap = pop(_nargs(d))
                st.append(('lambda', t, tuple(cap)))
            elif op in (0xb0, 0xac, 0xb1, 0xbf):
                if op == 0xb0 and st:
                    stores.append(('<return>', None, st[-1]))
                st.clear()
            elif op in (0xa7, 0xc8):
                st.clear()                                # jumps: forget the stack, keep going linearly
            elif 0x99 <= op <= 0x9e or op in (0xc6, 0xc7):
                pop()
            elif 0x9f <= op <= 0xa6:
                pop(2)
            else:
                st.clear()
        return stores

    # ---------------- enums and properties ----------------
    def enum_names(self, owner):
        """Constants of an enum in declaration order with their serialized names."""
        if owner in self.enum_cache:
            return self.enum_cache[owner]
        cf = self.get(owner)
        out = []
        if cf:
            for m in cf.methods_named('<clinit>'):
                pending = []

                def on_call(op, o, n, d, recv, args):
                    if n == '<init>' and recv and recv[0] == 'new' and self._sub(recv[1], owner):
                        strs = [a[1] for a in args if a[0] == 'str']
                        pending.append(strs)
                    return None

                for so, sn, val in self.run(cf, m, on_call):
                    if so == owner and val and val[0] == 'new' and self._sub(val[1], owner) and pending:
                        strs = pending.pop(0)
                        const = strs[0] if strs else sn
                        ser = strs[1] if len(strs) > 1 else const.lower()
                        out.append((const, ser))
        self.enum_cache[owner] = out
        return out

    def _sub(self, name, owner):
        cf = self.get(name)
        return name == owner or (cf is not None and cf.super == owner)

    def serialize(self, val):
        if val is None:
            return None
        if val[0] == 'field':
            owner, name = val[1], val[2]
            if owner == 'java/lang/Boolean':
                return name.lower()
            for const, ser in self.enum_names(owner):
                if const == name:
                    return ser
            return name.lower()
        if val[0] == 'bool':
            return 'true' if val[1] else 'false'
        if val[0] == 'int':
            return str(val[1])
        return None

    def prop(self, owner, name, depth=0):
        """Resolve a static Property field to (name, [values])."""
        key = (owner, name)
        if key in self.prop_cache:
            return self.prop_cache[key]
        self.prop_cache[key] = None
        res = None
        for o in self.ancestors(owner):
            cf = self.get(o)
            if not cf or not any(f['name'] == name for f in cf.fields):
                continue
            for m in cf.methods_named('<clinit>'):
                for so, sn, val in self.run(cf, m):
                    if so == o and sn == name:
                        res = self._propdef(val, depth)
            break
        self.prop_cache[key] = res
        return res

    def _propdef(self, val, depth):
        if val is None or depth > 8:
            return None
        if val[0] == 'field':
            return self.prop(val[1], val[2], depth + 1)
        if val[0] != 'call':
            return None
        owner, name, args = val[1], val[2], val[3]
        cls = owner.rsplit('/', 1)[-1]
        if name != 'create' or not args or args[0][0] != 'str':
            return None
        pname = args[0][1]
        if cls == 'BooleanProperty':
            return pname, ['true', 'false']
        if cls == 'IntegerProperty' and len(args) == 3 and args[1][0] == args[2][0] == 'int':
            return pname, [str(v) for v in range(args[1][1], args[2][1] + 1)]
        if cls == 'DirectionProperty':
            if len(args) == 1:
                return pname, list(DIRECTIONS)
            a = args[1]
            if a[0] == 'field' and a[2] == 'HORIZONTAL':
                return pname, list(HORIZONTAL)
            if a[0] == 'field' and a[2] == 'VERTICAL':
                return pname, ['down', 'up']
            if a[0] == 'arr':
                return pname, [x[2].lower() for x in a[1] if x and x[0] == 'field']
            if a[0] == 'lambda':
                return pname, ['?'] + list(DIRECTIONS)
            return pname, ['?'] + list(DIRECTIONS)
        if cls == 'EnumProperty' and len(args) >= 2 and args[1][0] == 'class':
            names = [s for _, s in self.enum_names(args[1][1])]
            if len(args) == 2:
                return pname, names
            a = args[2]
            if a[0] == 'arr':
                sub = [self.serialize(x) for x in a[1] if x]
                return pname, [s for s in sub if s]
            return pname, ['?'] + names                     # filtered by a predicate we do not run
        return None

    # ---------------- state definition ----------------
    def properties(self, cls, depth=0):
        """Ordered [(name, values)] declared by createBlockStateDefinition along the class chain."""
        cf = self.get(cls)
        if not cf or depth > 20:
            return []
        ms = [m for m in cf.methods if m['name'] == 'createBlockStateDefinition']
        if not ms:
            return self.properties(cf.super, depth + 1) if cf.super else []
        out = []

        def on_call(op, o, n, d, recv, args):
            if n == 'createBlockStateDefinition' and op == 0xb7:
                out.extend(self.properties(o if o != cls else cf.super, depth + 1))
            elif n == 'add' and o.endswith('StateDefinition$Builder'):
                vals = args[0][1] if args and args[0][0] == 'arr' else args
                for v in vals:
                    p = self._propdef(v, 0) if v else None
                    if p is None:
                        self.notes.append('%s: unresolved property %r' % (cls, v))
                    elif p[0] not in [q[0] for q in out]:
                        out.append(p)
                return recv
            elif n == 'createBlockStateDefinition':
                out.extend(self.properties(o, depth + 1))
            return None

        self.run(cf, ms[0], on_call, {0: ('this',), 1: ('builder',)})
        return out

    def defaults(self, cls, ctor_desc=None, depth=0):
        """Property values fixed by registerDefaultState along the constructor chain, in call order
        (super constructor first, then each registerDefaultState of this constructor)."""
        cf = self.get(cls)
        if not cf or depth > 20:
            return {}
        ctors = cf.methods_named('<init>')
        if ctor_desc:
            ctors = [m for m in ctors if m['desc'] == ctor_desc] or ctors
        best = {}
        for m in ctors:
            cur = [{}]
            touched = [False]

            def on_call(op, o, n, d, recv, args):
                if n == 'any' and o.endswith('StateDefinition'):
                    return ('state', {})
                if n == 'defaultBlockState':
                    return ('state', dict(cur[0]))
                if n == 'setValue' and recv and recv[0] == 'state' and len(args) == 2:
                    p = self._propdef(args[0], 0)
                    v = args[1]
                    if v[0] == 'call' and v[2] == 'valueOf' and v[3]:
                        v = ('bool', v[3][0][1]) if v[1] == 'java/lang/Boolean' and v[3][0][0] == 'int' else v[3][0]
                    new = dict(recv[1])
                    if p:
                        sv = self.serialize(v)
                        new[p[0]] = sv if sv is not None else '?'
                    return ('state', new)
                if n == 'registerDefaultState' and args and args[0][0] == 'state':
                    cur[0] = dict(args[0][1])
                    touched[0] = True
                if n == '<init>' and op == 0xb7 and recv and recv[0] == 'this':
                    target = cls if o == cls else o
                    if target == cls or self.is_block(target):
                        cur[0] = self.defaults(target, d, depth + 1)
                        touched[0] = touched[0] or bool(cur[0])
                return None

            self.run(cf, m, on_call, {0: ('this',)})
            if touched[0]:
                return cur[0]
            best = best or cur[0]
        return best

    def state_definition(self, cls, ctor_desc=None):
        props = self.properties(cls)
        explicit = self.defaults(cls, ctor_desc)
        default = {}
        for name, values in props:
            vals = [v for v in values if v != '?']
            default[name] = explicit.get(name, vals[0] if vals else '?')
        return props, default


def registrations(classes, cf, wanted):
    """Map block ids (in `wanted`) to the Block class their registration constructs: any call that
    receives both the id (a string, or an enum constant named like it) and a block factory
    (`() -> new X(...)`, `X::new` or a `new X(...)` value), in whatever argument order."""
    out = {}

    def factory(v):
        if v[0] == 'new' and classes.is_block(v[1]):
            return v[1], None
        if v[0] == 'lambda' and v[1]:
            kind, owner, name, desc = v[1]
            if name == '<init>' and classes.is_block(owner):
                return owner, desc
            if owner == cf.name:
                return _first_new(cf, name, desc, classes.is_block)
        return None

    def key(a, depth=0):
        if a[0] == 'str' and a[1] in wanted:
            return a[1]
        if a[0] == 'field' and a[2].lower() in wanted and a[3] == 'L%s;' % a[1]:
            return a[2].lower()
        if a[0] == 'call' and depth < 2:                  # e.g. ResourceLocation / resource("id")
            ks = [k for k in (key(b, depth + 1) for b in a[3]) if k]
            return ks[0] if len(ks) == 1 else None
        return None

    def on_call(op, owner, name, desc, recv, args):
        keys = [k for k in (key(a) for a in args) if k]
        facts = [f for f in (factory(a) for a in args) if f]
        if len(keys) == 1 and facts:
            out.setdefault(keys[0], facts[0])
        return None

    for m in cf.methods:
        classes.run(cf, m, on_call)
    return out


def _first_new(cf, name, desc, is_block):
    for m in cf.methods:
        if m['name'] == name and m['desc'] == desc:
            for pc, op, arg in cf.insns(m):
                if op == 0xb7:
                    owner, n, d = cf.ref(arg)
                    if n == '<init>' and is_block(owner):
                        return owner, d
    return None

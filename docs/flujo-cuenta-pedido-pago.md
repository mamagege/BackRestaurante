# Flujo mesa → cuenta → pedido → pago

## Ciclo completo

| Paso | Endpoint | Rol | Efecto |
|---|---|---|---|
| 1. Abrir cuenta | `POST /api/v1/cuentas` `{ "idMesa": 3 }` | CLIENTE, MESERO | Crea la cuenta `ABIERTA` y marca la mesa `OCUPADA`. Si la mesa ya tiene una cuenta abierta: **409** (RN-06). |
| 2. Pedir | `POST /api/v1/pedidos` `{ "idMesa": 3, "items": [...] }` | CLIENTE, MESERO | El pedido se asocia a la cuenta abierta de la mesa (`idCuenta` en la respuesta). Si la mesa no tiene cuenta abierta: **422**. |
| 3. Cocina | `PATCH /api/v1/pedidos/{id}/estado?estado=...` | COCINA, MESERO | `RECIBIDO → EN_PREPARACION → LISTO → ENTREGADO`. |
| 4. Ver total | `PATCH /api/v1/cuentas/{id}/total` | CLIENTE, MESERO | Recalcula el total con los pedidos **de esa cuenta**, sin contar los cancelados. |
| 5. Pagar | `PATCH /api/v1/cuentas/{id}/pago` | MESERO, ADMIN | Registra el pago, cierra la cuenta y libera la mesa (RN-07). |

La mesa **no** se ocupa ni se libera con endpoints propios. `PATCH /api/v1/mesas/{id}/estado` solo sirve para reservar una mesa libre (`RESERVADA`) o volver a dejarla `DISPONIBLE`.

## Pago

```json
PATCH /api/v1/cuentas/7/pago
{ "metodoPago": "EFECTIVO", "montoRecibido": 60000 }
```

| Método | Regla del monto | Pasarela externa |
|---|---|---|
| `EFECTIVO` | `montoRecibido >= total`. Se calcula `cambio`. | No |
| `TARJETA` | `montoRecibido == total` | Sí (`referenciaPago` en la respuesta) |
| `TRANSFERENCIA` | `montoRecibido == total` | Sí |

Respuestas de error del pago:
- **422** si:
  - hay pedidos sin entregar;
  - el monto es insuficiente o, con tarjeta o transferencia, distinto del total;
  - la cuenta ya está pagada;
  - la pasarela rechaza el pago.
- Si la pasarela rechaza, la cuenta **sigue abierta** y la mesa ocupada.
- Una cuenta sin consumos se puede cerrar con `montoRecibido: 0`.

La pasarela es hoy un adaptador simulado (`integration/PasarelaPagoSimulada`) que aprueba todo. La integración real consiste en implementar `service/IPasarelaPago` con el proveedor elegido.

## Despliegue: pedidos anteriores a este cambio

Los pedidos creados antes de este cambio no tienen `id_cuenta`, así que no suman en ninguna cuenta. Si al desplegar hay cuentas abiertas, conviene asociar sus pedidos con este SQL (PostgreSQL), que se ejecuta una sola vez:

```sql
UPDATE pedidos p
SET id_cuenta = c.id
FROM cuentas c
WHERE p.id_cuenta IS NULL
  AND c.estado = 'ABIERTA'
  AND c.id_mesa = p.id_mesa
  AND p.timestamp >= c.fecha_apertura;
```

Otra opción es desplegar cuando no haya cuentas abiertas; en ese caso no hace falta el SQL.

import type { operations } from './generated/schema';

type JsonContent<Content> = Content extends { 'application/json': infer Json }
  ? Json
  : Content extends { '*/*': infer AnyJson }
    ? AnyJson
    : never;

export type Operation<Name extends keyof operations> = operations[Name];

export type OperationQuery<Name extends keyof operations> =
  Operation<Name>['parameters'] extends { query?: infer Query }
    ? NonNullable<Query>
    : Operation<Name>['parameters'] extends { query: infer Query }
      ? Query
      : never;

export type OperationPath<Name extends keyof operations> =
  Operation<Name>['parameters'] extends { path?: infer Path }
    ? NonNullable<Path>
    : Operation<Name>['parameters'] extends { path: infer Path }
      ? Path
      : never;

export type OperationRequest<Name extends keyof operations> =
  Operation<Name> extends { requestBody?: { content: infer Content } }
    ? JsonContent<Content>
    : Operation<Name> extends { requestBody: { content: infer Content } }
      ? JsonContent<Content>
      : never;

type SuccessStatus<Name extends keyof operations> =
  200 extends keyof Operation<Name>['responses']
    ? 200
    : 201 extends keyof Operation<Name>['responses']
      ? 201
      : 204 extends keyof Operation<Name>['responses']
        ? 204
        : keyof Operation<Name>['responses'];

export type OperationResponse<
  Name extends keyof operations,
  Status extends keyof Operation<Name>['responses'] = SuccessStatus<Name>,
> = Operation<Name>['responses'][Status] extends { content: infer Content }
  ? JsonContent<Content>
  : never;

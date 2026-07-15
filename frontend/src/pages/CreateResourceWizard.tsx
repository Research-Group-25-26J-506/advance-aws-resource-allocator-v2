import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Ajv, { ErrorObject } from "ajv";
import Alert from "@cloudscape-design/components/alert";
import Box from "@cloudscape-design/components/box";
import Checkbox from "@cloudscape-design/components/checkbox";
import ColumnLayout from "@cloudscape-design/components/column-layout";
import Container from "@cloudscape-design/components/container";
import ContentLayout from "@cloudscape-design/components/content-layout";
import FormField from "@cloudscape-design/components/form-field";
import Header from "@cloudscape-design/components/header";
import Input from "@cloudscape-design/components/input";
import Select from "@cloudscape-design/components/select";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Spinner from "@cloudscape-design/components/spinner";
import Textarea from "@cloudscape-design/components/textarea";
import Toggle from "@cloudscape-design/components/toggle";
import Wizard from "@cloudscape-design/components/wizard";
import { api, uuidv7 } from "../api/client";
import type { Environment, TemplateDetail } from "../api/types";

interface SchemaProperty {
  type?: string;
  title?: string;
  enum?: string[];
  default?: unknown;
  minimum?: number;
  maximum?: number;
  pattern?: string;
  "x-help"?: string;
  "x-sensitive"?: boolean;
  format?: string;
}

interface FormSchema {
  properties?: Record<string, SchemaProperty>;
  required?: string[];
}

/**
 * Dynamic Create Resource Wizard (2.04): renders any template's JSON Schema (draft-07 subset,
 * no external $ref) — one component covers every template. Ajv validates client-side; the
 * Idempotency-Key (UUIDv7) is generated once per mount so retries reuse the same key. Drafts
 * persist to sessionStorage keyed by templateId@version.
 */
export default function CreateResourceWizard() {
  const { templateId } = useParams<{ templateId: string }>();
  const navigate = useNavigate();

  const [detail, setDetail] = useState<TemplateDetail | null>(null);
  const [regions, setRegions] = useState<string[]>([]);
  const [step, setStep] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  // Basics
  const [resourceName, setResourceName] = useState("");
  const [environment, setEnvironment] = useState<Environment>("DEV");
  const [region, setRegion] = useState("us-east-1");
  const [description, setDescription] = useState("");
  // Configuration (schema-driven)
  const [config, setConfig] = useState<Record<string, unknown>>({});
  // Review
  const [confirmed, setConfirmed] = useState(false);

  const idempotencyKey = useRef(uuidv7()); // once per mount — retries reuse it (2.04 enhancement)

  const draftKey = detail ? `wizard-draft:${detail.template.id}@${detail.latestVersion}` : null;

  useEffect(() => {
    if (!templateId) return;
    api.getTemplate(templateId).then((d) => {
      setDetail(d);
      const restored = sessionStorage.getItem(`wizard-draft:${d.template.id}@${d.latestVersion}`);
      if (restored) {
        const draft = JSON.parse(restored);
        setResourceName(draft.resourceName ?? "");
        setEnvironment(draft.environment ?? "DEV");
        setRegion(draft.region ?? "us-east-1");
        setConfig(draft.config ?? {});
      } else {
        // Seed defaults from the schema
        const schema: FormSchema = JSON.parse(d.schema);
        const seeded: Record<string, unknown> = {};
        Object.entries(schema.properties ?? {}).forEach(([key, prop]) => {
          if (prop.default !== undefined) seeded[key] = prop.default;
        });
        setConfig(seeded);
      }
    }).catch((e) => setError(String(e)));
  }, [templateId]);

  useEffect(() => {
    api.regions(environment).then((r) => {
      setRegions(r);
      if (!r.includes(region)) setRegion(r[0]);
    }).catch(() => setRegions(["us-east-1"]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [environment]);

  useEffect(() => {
    if (draftKey) {
      sessionStorage.setItem(draftKey, JSON.stringify({ resourceName, environment, region, config }));
    }
  }, [draftKey, resourceName, environment, region, config]);

  const schema: FormSchema | null = useMemo(() => (detail ? JSON.parse(detail.schema) : null), [detail]);

  if (!detail || !schema) {
    return (
      <Box textAlign="center" padding="xxl">
        {error ? <Alert type="error">{error}</Alert> : <Spinner size="large" />}
      </Box>
    );
  }

  const validateConfiguration = (): boolean => {
    const ajv = new Ajv({ allErrors: true, strict: false });
    const validate = ajv.compile(JSON.parse(detail.schema));
    const valid = validate(config);
    if (valid) {
      setFieldErrors({});
      return true;
    }
    // Ajv instancePath → Cloudscape errorText, implemented once (2.04 enhancement)
    const mapped: Record<string, string> = {};
    (validate.errors ?? []).forEach((err: ErrorObject) => {
      const field = err.instancePath.replace(/^\//, "") || String(err.params?.missingProperty ?? "");
      mapped[field] = err.message ?? "invalid";
    });
    setFieldErrors(mapped);
    setError("Some configuration values are invalid — fix the highlighted fields.");
    return false;
  };

  const submit = async () => {
    if (!confirmed) {
      setError("Confirm that you understand this provisions real AWS resources before submitting.");
      return;
    }
    if (!validateConfiguration()) return;
    setSubmitting(true);
    setError(null);
    try {
      const created = await api.createRequest(
        {
          templateId: detail.template.id,
          environment,
          region,
          resourceName,
          description: description || undefined,
          configuration: config,
        },
        idempotencyKey.current,
      );
      if (draftKey) sessionStorage.removeItem(draftKey);
      navigate(`/requests/${created.id}`);
    } catch (e) {
      setError(String(e));
      setSubmitting(false);
    }
  };

  const renderField = (key: string, prop: SchemaProperty) => {
    const label = prop.title ?? key;
    const value = config[key];
    const setValue = (v: unknown) => setConfig((c) => ({ ...c, [key]: v }));
    const errorText = fieldErrors[key];
    const sensitive = prop["x-sensitive"] || prop.format === "password";

    if (prop.enum) {
      return (
        <FormField key={key} label={label} errorText={errorText} description={prop["x-help"]}>
          <Select
            selectedOption={value ? { value: String(value), label: String(value) } : null}
            options={prop.enum.map((o) => ({ value: o, label: o }))}
            onChange={(e) => setValue(e.detail.selectedOption.value)}
          />
        </FormField>
      );
    }
    if (prop.type === "boolean") {
      return (
        <FormField key={key} label={label} errorText={errorText} description={prop["x-help"]}>
          <Toggle checked={Boolean(value)} onChange={(e) => setValue(e.detail.checked)}>
            {label}
          </Toggle>
        </FormField>
      );
    }
    if (prop.type === "integer" || prop.type === "number") {
      return (
        <FormField
          key={key}
          label={label}
          errorText={errorText}
          description={prop["x-help"]}
          constraintText={
            prop.minimum !== undefined || prop.maximum !== undefined
              ? `Range: ${prop.minimum ?? "-∞"} to ${prop.maximum ?? "∞"}`
              : undefined
          }
        >
          <Input
            type="number"
            value={value === undefined ? "" : String(value)}
            onChange={(e) => setValue(e.detail.value === "" ? undefined : Number(e.detail.value))}
          />
        </FormField>
      );
    }
    return (
      <FormField key={key} label={label} errorText={errorText} description={prop["x-help"]}>
        <Input
          type={sensitive ? "password" : "text"}
          value={value === undefined ? "" : String(value)}
          onChange={(e) => setValue(e.detail.value === "" ? undefined : e.detail.value)}
        />
      </FormField>
    );
  };

  const reviewJson = JSON.stringify(
    Object.fromEntries(
      Object.entries(config).filter(([key]) => {
        const prop = schema.properties?.[key];
        return !(prop?.["x-sensitive"] || prop?.format === "password"); // masked fields excluded
      }),
    ),
    null,
    2,
  );

  return (
    <ContentLayout header={<Header variant="h1">Provision: {detail.template.displayName}</Header>}>
      {error && (
        <Box padding={{ bottom: "m" }}>
          <Alert type="error" dismissible onDismiss={() => setError(null)}>
            {error}
          </Alert>
        </Box>
      )}
      <Wizard
        i18nStrings={{
          stepNumberLabel: (n) => `Step ${n}`,
          collapsedStepsLabel: (n, total) => `Step ${n} of ${total}`,
          cancelButton: "Cancel",
          previousButton: "Previous",
          nextButton: "Next",
          submitButton: "Submit request",
        }}
        activeStepIndex={step}
        onNavigate={(e) => {
          if (e.detail.requestedStepIndex > 1 && step === 1 && !validateConfiguration()) return;
          setError(null);
          setStep(e.detail.requestedStepIndex);
        }}
        onCancel={() => navigate("/catalog")}
        onSubmit={submit}
        isLoadingNextStep={submitting}
        steps={[
          {
            title: "Basics",
            content: (
              <Container>
                <SpaceBetween size="m">
                  <FormField label="Resource name" constraintText="Lowercase letters, digits, hyphens; 3–63 chars.">
                    <Input value={resourceName} onChange={(e) => setResourceName(e.detail.value)} />
                  </FormField>
                  <FormField label="Environment">
                    <Select
                      selectedOption={{ value: environment, label: environment }}
                      options={["DEV", "STG", "PROD"].map((v) => ({ value: v, label: v }))}
                      onChange={(e) => setEnvironment(e.detail.selectedOption.value as Environment)}
                    />
                  </FormField>
                  {environment === "PROD" && (
                    <Alert type="warning" header="PROD requires approval">
                      This request will wait in the approvals inbox until an approver signs off.
                    </Alert>
                  )}
                  <FormField label="Region">
                    <Select
                      selectedOption={{ value: region, label: region }}
                      options={regions.map((r) => ({ value: r, label: r }))}
                      onChange={(e) => setRegion(e.detail.selectedOption.value!)}
                    />
                  </FormField>
                  <FormField label="Description — optional">
                    <Textarea value={description} onChange={(e) => setDescription(e.detail.value)} rows={2} />
                  </FormField>
                </SpaceBetween>
              </Container>
            ),
          },
          {
            title: "Configuration",
            content: (
              <Container>
                <SpaceBetween size="m">
                  {Object.entries(schema.properties ?? {}).map(([key, prop]) => renderField(key, prop))}
                </SpaceBetween>
              </Container>
            ),
          },
          {
            title: "Tags",
            content: (
              <Container>
                <ColumnLayout columns={2} variant="text-grid">
                  <div>
                    <Box variant="awsui-key-label">Owner</Box>
                    <Box>derived from your login</Box>
                  </div>
                  <div>
                    <Box variant="awsui-key-label">CostCenter</Box>
                    <Box>derived from your team</Box>
                  </div>
                  <div>
                    <Box variant="awsui-key-label">Environment</Box>
                    <Box>{environment}</Box>
                  </div>
                  <div>
                    <Box variant="awsui-key-label">ManagedBy</Box>
                    <Box>Platform</Box>
                  </div>
                </ColumnLayout>
                <Box padding={{ top: "m" }} color="text-status-inactive">
                  Mandatory tags are computed server-side and cannot be overridden. Additional tags land with
                  the TagEditor in a later iteration.
                </Box>
              </Container>
            ),
          },
          {
            title: "Review and submit",
            content: (
              <SpaceBetween size="m">
                <Container header={<Header>Summary</Header>}>
                  <ColumnLayout columns={2} variant="text-grid">
                    <div>
                      <Box variant="awsui-key-label">Template</Box>
                      <Box>
                        {detail.template.displayName} v{detail.latestVersion}
                      </Box>
                    </div>
                    <div>
                      <Box variant="awsui-key-label">Resource name</Box>
                      <Box>{resourceName || "—"}</Box>
                    </div>
                    <div>
                      <Box variant="awsui-key-label">Environment / Region</Box>
                      <Box>
                        {environment} / {region}
                      </Box>
                    </div>
                  </ColumnLayout>
                </Container>
                <Container header={<Header>Configuration (sensitive fields excluded)</Header>}>
                  <Textarea value={reviewJson} readOnly rows={10} />
                </Container>
                <Checkbox checked={confirmed} onChange={(e) => setConfirmed(e.detail.checked)}>
                  I understand this will provision real AWS resources
                </Checkbox>
                {!confirmed && (
                  <Box color="text-status-inactive" fontSize="body-s">
                    Tick the confirmation to enable Submit.
                  </Box>
                )}
              </SpaceBetween>
            ),
          },
        ]}
      />
    </ContentLayout>
  );
}
